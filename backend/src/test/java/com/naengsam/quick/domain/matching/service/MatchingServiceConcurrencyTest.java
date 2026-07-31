package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.Order;
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
 * MatchingEngine이 실제로 액션을 단일 스레드로 직렬화하는지 검증한다. Mock이 아닌 실제 MatchingEngine을 띄워 여러 요청 스레드에서 동시에 액션을 제출했을 때, 내부 HashMap이
 * 경합 없이 일관된 상태로 수렴하는지 확인한다.
 */
class MatchingServiceConcurrencyTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private ExecutorService requestThreads;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        matchingEngine.start();
        matchingService = new MatchingService(matchingEngine, mock(com.naengsam.quick.global.sse.SseService.class));
        requestThreads = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        requestThreads.shutdownNow();
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
        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        matchingService.startMatching(order);
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId).isPresent(), Duration.ofSeconds(5));

        UUID offerId = matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().getFirst().offerId();

        int concurrentAccepts = 50;
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

        awaitUntil(() -> statusOf(orderId, offerId) == MatchingService.MatchOfferStatus.PENDING_BOORMI_CONFIRMATION,
                Duration.ofSeconds(5));

        // 경합 상황에서도 여러 번의 잘못된 재수락 시도는 상태 전이 예외로 무시될 뿐,
        // 엔진 스레드 자체는 죽지 않고 이후 액션을 계속 처리한다.
        UUID afterRaceDreamiId = UUID.randomUUID();
        matchingService.registerDreami(afterRaceDreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(afterRaceDreamiId), Duration.ofSeconds(5));

        assertThat(statusOf(orderId, offerId)).isEqualTo(MatchingService.MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
    }

    @Test
    void 동시에_같은_주문에_매칭을_시작해도_그룹은_단_하나만_생성된다() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        matchingService.registerDreami(dreamiId, location);
        awaitUntil(() -> getDreamiMap().containsKey(dreamiId), Duration.ofSeconds(5));

        int concurrentStarts = 30;
        CountDownLatch ready = new CountDownLatch(concurrentStarts);
        CountDownLatch go = new CountDownLatch(1);
        Set<MatchingService.OrderOfferGroup> observedGroups = new CopyOnWriteArraySet<>();
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

        assertThat(observedGroups).hasSize(1);
        assertThat(observedGroups.iterator().next().offers()).hasSizeLessThanOrEqualTo(1);
    }

    private MatchingService.MatchOfferStatus statusOf(UUID orderId, UUID offerId) {
        return matchingService.findOrderOfferGroup(orderId).orElseThrow()
                .offers().stream()
                .filter(offer -> offer.offerId().equals(offerId))
                .findFirst()
                .orElseThrow()
                .status();
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
    private Map<UUID, MatchingService.WaitingDreami> getDreamiMap() {
        return (Map<UUID, MatchingService.WaitingDreami>)
                ReflectionTestUtils.getField(matchingService, "dreamiMap");
    }
}
