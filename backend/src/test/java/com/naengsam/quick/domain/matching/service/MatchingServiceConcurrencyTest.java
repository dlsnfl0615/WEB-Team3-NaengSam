package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.scheduler.MatchingScheduler;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.sse.SseService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MatchingScheduler가 실제로 액션을 단일 스레드로 직렬화하는지 검증한다. Mock이 아닌 실제 MatchingScheduler를 띄워 여러 요청 스레드에서 동시에 액션을 제출했을 때, 내부 HashMap이
 * 경합 없이 일관된 상태로 수렴하는지 확인한다.
 */
class MatchingServiceConcurrencyTest {

    private MatchingScheduler matchingScheduler;
    private MatchingService matchingService;
    private ExecutorService requestThreads;

    @BeforeEach
    void setUp() {
        matchingScheduler = new MatchingScheduler();
        matchingScheduler.start();
        matchingService = new MatchingService(matchingScheduler, mock(SseService.class),
                mock(MatchingBatchDispatcher.class), mock(DeliveryService.class), Clock.systemDefaultZone(),
        matchingEngine = new MatchingEngine();
        matchingEngine.start();
        matchingService = new MatchingService(matchingEngine, mock(SseService.class), mock(MatchingActionScheduler.class),
                mock(MatchingBatchDispatcher.class), mock(DeliveryService.class),
                Clock.systemDefaultZone(),
                mock(MatchingAssignmentProblemAssembler.class), mock(MatchingAssignmentPolicy.class),
                mock(MatchingPlanApplier.class), mock(MatchingPolicyProperties.class), new GeoDistanceCalculator());
        requestThreads = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        requestThreads.shutdownNow();
        matchingScheduler.shutdown();
    }

    @Test
    void 여러_스레드가_동시에_드리미를_등록해도_모두_유실없이_등록된다() throws InterruptedException {
        int dreamiCount = 200;
        List<UUID> dreamiIds = IntStream.range(0, dreamiCount)
                .mapToObj(i -> UUID.randomUUID())
                .toList();
        GeoPoint location = mock(GeoPoint.class);

        CountDownLatch submitted = new CountDownLatch(dreamiCount);
        for (UUID dreamiId : dreamiIds) {
            requestThreads.submit(() -> {
                matchingService.registerDreami(dreamiId, location);
                submitted.countDown();
            });
        }
        assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();

        awaitUntil(() -> getDreamiMap().size() == dreamiCount, Duration.ofSeconds(5));

        assertThat(getDreamiMap().keySet()).containsExactlyInAnyOrderElementsOf(dreamiIds);
    }

    @Test
    void 동시에_같은_제안을_여러번_수락해도_단_한번만_상태가_전이된다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        matchingService.startMatching(order);
        // markDirty()가 mock이라 실제 배치 실행으로 이어지지 않으므로, 재매칭 스캔으로 첫 오퍼 라운드를 직접 유도한다.
        matchingService.scheduleRematchWaitingGroups();
        awaitFirstOfferCreated(orderId);

        UUID offerId = matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().getFirst().offerId();

        // requestThreads 풀 크기(16)를 넘으면 ready/go 래치 장벽에서 데드락이 나므로 풀 크기 이하로 제한한다.
        int concurrentAccepts = 16;
        CountDownLatch ready = new CountDownLatch(concurrentAccepts);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < concurrentAccepts; i++) {
            requestThreads.submit(() -> {
                ready.countDown();
                awaitLatch(go);
                matchingService.acceptByDreami(offerId);
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        awaitUntil(() -> statusOf(orderId, offerId) == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION,
                Duration.ofSeconds(5));

        // 경합 상황에서도 여러 번의 잘못된 재수락 시도는 상태 전이 예외로 무시될 뿐,
        // 엔진 스레드 자체는 죽지 않고 이후 액션을 계속 처리한다.
        UUID afterRaceDreamiId = UUID.randomUUID();
        matchingService.registerDreami(afterRaceDreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(afterRaceDreamiId), Duration.ofSeconds(5));

        assertThat(statusOf(orderId, offerId)).isEqualTo(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
    }

    @Test
    void 동시에_같은_주문에_매칭을_시작해도_그룹은_단_하나만_생성된다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        // requestThreads 풀 크기(16)를 넘으면 ready/go 래치 장벽에서 데드락이 나므로 풀 크기 이하로 제한한다.
        int concurrentStarts = 16;
        CountDownLatch ready = new CountDownLatch(concurrentStarts);
        CountDownLatch go = new CountDownLatch(1);
        Set<OrderOfferGroup> observedGroups = new CopyOnWriteArraySet<>();
        for (int i = 0; i < concurrentStarts; i++) {
            requestThreads.submit(() -> {
                ready.countDown();
                awaitLatch(go);
                matchingService.startMatching(order);
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId).isPresent(), Duration.ofSeconds(5));

        // 큐가 완전히 비워질 시간을 준 뒤(추가 액션 하나를 흘려보내 확인), 그룹이 계속 하나로만 유지되는지 확인한다.
        UUID flushDreamiId = UUID.randomUUID();
        matchingService.registerDreami(flushDreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(flushDreamiId), Duration.ofSeconds(5));

        for (int i = 0; i < concurrentStarts; i++) {
            matchingService.findOrderOfferGroup(orderId).ifPresent(observedGroups::add);
        }

        // 오퍼 생성은 이제 매칭 시작과 분리되어(dirty 표시 후 배치/재매칭으로 지연) 이 테스트의 관심사가 아니므로,
        // 동시 시작에도 그룹이 단 하나만 생성/유지되는지만 확인한다.
        assertThat(observedGroups).hasSize(1);
    }

    @Test
    void 수락이_취소보다_먼저_큐에_들어오면_수락된_오퍼는_부르미_거절로_반영된다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        matchingService.startMatching(order);
        // markDirty()가 mock이라 실제 배치 실행으로 이어지지 않으므로, 재매칭 스캔으로 첫 오퍼 라운드를 직접 유도한다.
        matchingService.scheduleRematchWaitingGroups();
        awaitFirstOfferCreated(orderId);

        UUID offerId = matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().getFirst().offerId();

        // when (수락을 먼저 제출한 뒤 취소를 제출 - 큐에 들어간 순서대로 처리되어야 한다)
        matchingService.acceptByDreami(offerId);
        matchingService.cancelOrderByBoormi(orderId);

        // then
        // 오퍼 상태(BOORMI_REJECTED)와 방 상태(CANCELLED)는 applyCancelOrderByBoormi 한 액션 안에서 순차적으로
        // 바뀌므로, 오퍼 상태만 기다리면 방 상태가 아직 갱신되기 전(OPEN)을 관찰할 수 있다. 최종적으로 확인할
        // 조건(그룹 CANCELLED)까지 함께 기다려야 한다.
        awaitUntil(() -> statusOf(orderId, offerId) == MatchOfferStatus.BOORMI_REJECTED
                        && matchingService.findOrderOfferGroup(orderId).orElseThrow().status()
                        == OrderOfferGroupStatus.CANCELLED,
                Duration.ofSeconds(5));

        assertThat(matchingService.findOrderOfferGroup(orderId).orElseThrow().status())
                .isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);
    }

    @Test
    void 취소가_수락보다_먼저_큐에_들어오면_뒤늦은_수락은_반영되지_않는다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        matchingService.startMatching(order);
        // markDirty()가 mock이라 실제 배치 실행으로 이어지지 않으므로, 재매칭 스캔으로 첫 오퍼 라운드를 직접 유도한다.
        matchingService.scheduleRematchWaitingGroups();
        awaitFirstOfferCreated(orderId);

        UUID offerId = matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().getFirst().offerId();

        // when (취소를 먼저 제출한 뒤 수락을 제출 - 취소가 먼저 처리되어 오퍼가 회수된 상태에서 수락이 뒤늦게 도착한다)
        matchingService.cancelOrderByBoormi(orderId);
        matchingService.acceptByDreami(offerId);

        // then (취소 처리로 WITHDRAWN 된 뒤, 뒤늦은 수락은 무시되어야 한다)
        // 오퍼 상태(WITHDRAWN)와 방 상태(CANCELLED)는 applyCancelOrderByBoormi 한 액션 안에서 순차적으로
        // 바뀌므로, 오퍼 상태만 기다리면 방 상태가 아직 갱신되기 전(OPEN)을 관찰할 수 있다. 최종적으로 확인할
        // 조건(그룹 CANCELLED)까지 함께 기다려야 한다.
        awaitUntil(() -> statusOf(orderId, offerId) == MatchOfferStatus.WITHDRAWN
                        && matchingService.findOrderOfferGroup(orderId).orElseThrow().status()
                        == OrderOfferGroupStatus.CANCELLED,
                Duration.ofSeconds(5));

        assertThat(matchingService.findOrderOfferGroup(orderId).orElseThrow().status())
                .isEqualTo(OrderOfferGroupStatus.CANCELLED);

        // 큐가 완전히 비워질 시간을 준 뒤(추가 액션 하나를 흘려보내 확인), 수락이 뒤늦게 반영되지 않았는지 재확인한다.
        UUID flushDreamiId = UUID.randomUUID();
        matchingService.registerDreami(flushDreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(flushDreamiId), Duration.ofSeconds(5));

        assertThat(statusOf(orderId, offerId)).isEqualTo(MatchOfferStatus.WITHDRAWN);
    }

    @Test
    void timeout과_사용자_응답이_거의_동시에_들어와도_큐_처리_순서대로_단_한번만_유효하게_전이된다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        matchingService.startMatching(order);
        // markDirty()가 mock이라 실제 배치 실행으로 이어지지 않으므로, 재매칭 스캔으로 첫 오퍼 라운드를 직접 유도한다.
        matchingService.scheduleRematchWaitingGroups();
        awaitFirstOfferCreated(orderId);

        UUID offerId = matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().getFirst().offerId();

        // when (DelayQueue timeout 워커와 사용자 응답 스레드가 거의 동시에 같은 오퍼에 대해 서로 다른 액션을 제출한다)
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        requestThreads.submit(() -> {
            ready.countDown();
            awaitLatch(go);
            matchingService.expireDreamiOffer(offerId);
        });
        requestThreads.submit(() -> {
            ready.countDown();
            awaitLatch(go);
            matchingService.acceptByDreami(offerId);
        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        // then (엔진 큐에 도착한 순서대로 처리되어, 둘 중 하나만 유효하게 반영된다)
        awaitUntil(() -> statusOf(orderId, offerId) == MatchOfferStatus.DREAMI_EXPIRED
                        || statusOf(orderId, offerId) == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION,
                Duration.ofSeconds(5));

        MatchOfferStatus finalStatus = statusOf(orderId, offerId);
        assertThat(finalStatus).isIn(
                MatchOfferStatus.DREAMI_EXPIRED,
                MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);

        // 나중에 도착한 액션은 잘못된 상태 전이 예외로 무시될 뿐, 엔진 스레드는 죽지 않고 이후 액션을 계속 처리해야 한다.
        UUID flushDreamiId = UUID.randomUUID();
        matchingService.registerDreami(flushDreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(flushDreamiId), Duration.ofSeconds(5));

        // 상태가 그 사이 다시 바뀌지 않았는지(단 한 번만 전이) 재확인한다.
        assertThat(statusOf(orderId, offerId)).isEqualTo(finalStatus);
    }

    private MatchOfferStatus statusOf(UUID orderId, UUID offerId) {
        return matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().stream()
                .filter(offer -> offer.offerId().equals(offerId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private void awaitFirstOfferCreated(UUID orderId) {
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                        .map(group -> !group.offers().isEmpty())
                        .orElse(false),
                Duration.ofSeconds(5));
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("대기 중 인터럽트되었습니다.");
            }
        }
        fail("조건이 제한 시간 내에 충족되지 않았습니다.");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, WaitingDreami> getDreamiMap() {
        return (Map<UUID, WaitingDreami>)
                ReflectionTestUtils.getField(matchingService, "dreamiMap");
    }
}
