package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.BoormiOfferExpirationService;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.order.service.PendingOfferStateService;
import com.naengsam.quick.global.notification.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link PeriodicMatchingBatchScheduler}가 실제 {@link MatchingEngine} 위에서 반복 배치를 구동할 때, 등록/거절/timeout 같은
 * 개별 이벤트가 배치를 별도로 깨우지 않아도 다음 반복 사이클에서 자동으로 반영되는지 실제 시간 흐름으로 검증한다. 빠르게 끝나도록 batchInterval은
 * 짧은 밀리초 단위로 둔다.
 */
class PeriodicMatchingBatchIntegrationTest {

    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    private static final Duration BATCH_INTERVAL = Duration.ofMillis(30);

    private MatchingEngine matchingEngine;
    private OrderService orderService;
    private PendingOfferStateService pendingOfferStateService;

    @AfterEach
    void tearDown() {
        matchingEngine.shutdown();
    }

    private static MatchingPolicyProperties matchingPolicyProperties(int maxConcurrentOffers) {
        return matchingPolicyProperties(maxConcurrentOffers, EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(3)));
    }

    // OUTCOME_COOLDOWN 정책 하에서, 드리미 응답 timeout 쿨다운만 짧게 두고 나머지는 그대로 재사용한다.
    private static MatchingPolicyProperties matchingPolicyPropertiesWithCooldown(
            int maxConcurrentOffers, Duration dreamiExpirationCooldown) {
        return matchingPolicyProperties(maxConcurrentOffers, EligibilityPolicyType.OUTCOME_COOLDOWN,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(5), Duration.ofMinutes(10), dreamiExpirationCooldown));
    }

    private static MatchingPolicyProperties matchingPolicyProperties(
            int maxConcurrentOffers, EligibilityPolicyType eligibilityPolicyType,
            MatchingPolicyProperties.Cooldown cooldown) {
        return new MatchingPolicyProperties(
                BATCH_INTERVAL,
                maxConcurrentOffers,
                OfferQuotaMode.FIXED,
                5,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                eligibilityPolicyType,
                cooldown,
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    private static MatchingEligibilityPolicy matchingEligibilityPolicy(MatchingPolicyProperties properties) {
        return switch (properties.eligibilityPolicy()) {
            case LEGACY -> new LegacyOfferPolicy();
            case OUTCOME_COOLDOWN -> {
                MatchingPolicyProperties.Cooldown cooldown = properties.cooldown();
                yield new OutcomeCooldownOfferPolicy(
                        cooldown.dreamiRejection(), cooldown.boormiRejection(), cooldown.dreamiExpiration());
            }
        };
    }

    private Orders orderMock(UUID orderId) {
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        lenient().when(order.getOrderCd()).thenReturn(OrderCd.MATCHING);
        lenient().when(orderService.findOrder(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    private MatchingService newRunningMatchingService(int maxConcurrentOffers) {
        return newRunningMatchingService(matchingPolicyProperties(maxConcurrentOffers));
    }

    private MatchingService newRunningMatchingService(MatchingPolicyProperties properties) {
        matchingEngine = new MatchingEngine();
        matchingEngine.start();

        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.isReachableNow(any())).thenReturn(true);
        DeliveryService deliveryService = mock(DeliveryService.class);
        Clock clock = Clock.systemDefaultZone();

        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(500.0);

        MatchingEligibilityPolicy eligibilityPolicy = matchingEligibilityPolicy(properties);
        MatchingAssignmentPolicy assignmentPolicy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());
        orderService = mock(OrderService.class);
        pendingOfferStateService = mock(PendingOfferStateService.class);
        lenient().when(pendingOfferStateService.isCurrent(any(), any())).thenReturn(true);
        // 배치 오퍼 생성 직전 가드가 findOrders(orderId 목록)를 한 번에 호출하므로, orderMock()이 개별 orderId에
        // 등록해 둔 findOrder 스텁으로 위임한다.
        lenient().when(orderService.findOrders(any())).thenAnswer(invocation -> {
            Collection<UUID> orderIds = invocation.getArgument(0);
            Map<UUID, Orders> result = new HashMap<>();
            for (UUID id : orderIds) {
                orderService.findOrder(id).ifPresent(order -> result.put(id, order));
            }
            return result;
        });
        MatchingPlanApplier matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(eligibilityPolicy), mock(MatchingService.class),
                notificationService, OFFER_TTL, orderService);

        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, new MatchingAssignmentProblemFactory(eligibilityPolicy),
                properties, clock);

        BoormiOfferExpirationService boormiOfferExpirationService = mock(BoormiOfferExpirationService.class);
        // 이 파일은 DB 경합을 다루지 않으므로, 부르미 timeout이 항상 DB 갱신에 성공한 것으로 둔다.
        when(boormiOfferExpirationService.expire(any(), any())).thenReturn(true);

        MatchingService matchingService = new MatchingService(
                matchingEngine, notificationService, deliveryService,
                clock,
                assembler, assignmentPolicy, matchingPlanApplier, properties, geoDistanceCalculator,
                new SimpleMeterRegistry(), boormiOfferExpirationService, orderService, pendingOfferStateService);

        new PeriodicMatchingBatchScheduler(matchingEngine, properties, matchingService).start();

        return matchingService;
    }

    @Test
    void 반복_배치가_별도_트리거_없이_주기적으로_실행되어_대기중인_매칭을_처리한다() {
        MatchingService matchingService = newRunningMatchingService(3);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        // when (public API만 사용 - applyRunMatchingAssignmentCycle을 직접 호출하지 않는다)
        matchingService.registerDreami(dreamiId, location);
        matchingService.startMatching(orderMock(orderId));

        // then (반복 예약된 배치 사이클이 스스로 실행되어 오퍼를 만든다)
        awaitUntil(() -> {
            OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElse(null);
            return group != null && !group.offers().isEmpty();
        }, Duration.ofSeconds(2));

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
    }

    @Test
    void 매칭_대상이_없는_빈_배치가_반복돼도_예외_없이_이후_등록된_매칭을_정상_처리한다() {
        MatchingService matchingService = newRunningMatchingService(3);

        // given (아무 것도 등록하지 않은 채로 몇 차례의 빈 배치가 흘러가게 둔다)
        sleep(BATCH_INTERVAL.multipliedBy(5));

        // when (그 뒤에야 실제 매칭 대상이 등록된다)
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        matchingService.registerDreami(dreamiId, location);
        matchingService.startMatching(orderMock(orderId));

        // then (빈 배치를 반복하던 워커 스레드가 죽지 않고 계속 동작해, 새 매칭도 정상적으로 처리된다)
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> !group.offers().isEmpty())
                .orElse(false), Duration.ofSeconds(2));
    }

    @Test
    void 드리미가_거절해도_별도_트리거_없이_다음_배치에서_다른_드리미에게_자동으로_재제안된다() {
        MatchingService matchingService = newRunningMatchingService(1);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.registerDreami(dreamiId1, location);
        matchingService.registerDreami(dreamiId2, location);
        matchingService.startMatching(orderMock(orderId));

        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> !group.offers().isEmpty())
                .orElse(false), Duration.ofSeconds(2));

        MatchOffer firstOffer = matchingService.findOrderOfferGroup(orderId).orElseThrow().offers().getFirst();
        UUID rejectedDreamiId = firstOffer.dreamiId();

        // when (수동으로 다음 배치를 트리거하지 않고, 거절만 한다)
        matchingService.rejectByDreami(firstOffer.offerId());

        // then (반복 배치가 스스로 실행돼 남은 드리미에게 자동으로 재제안한다)
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> group.offers().stream()
                        .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                                && !offer.dreamiId().equals(rejectedDreamiId)))
                .orElse(false), Duration.ofSeconds(2));
    }

    @Test
    void 드리미_응답_timeout이_발생해도_별도_트리거_없이_다음_배치에서_자동으로_재제안된다() {
        MatchingService matchingService = newRunningMatchingService(1);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.registerDreami(dreamiId1, location);
        matchingService.registerDreami(dreamiId2, location);
        matchingService.startMatching(orderMock(orderId));

        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> !group.offers().isEmpty())
                .orElse(false), Duration.ofSeconds(2));

        MatchOffer firstOffer = matchingService.findOrderOfferGroup(orderId).orElseThrow().offers().getFirst();
        UUID expiredDreamiId = firstOffer.dreamiId();

        // when (수동으로 다음 배치를 트리거하지 않고, timeout만 발생시킨다)
        matchingService.expireDreamiOffer(firstOffer.offerId());

        // then
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> group.offers().stream()
                        .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                                && !offer.dreamiId().equals(expiredDreamiId)))
                .orElse(false), Duration.ofSeconds(2));
    }

    @Test
    void 드리미_응답_timeout_쿨다운_중에는_같은_드리미가_배치에서_제외되고_쿨다운이_끝나면_새_오퍼가_생성된다() {
        Duration dreamiExpirationCooldown = Duration.ofMillis(150);
        MatchingService matchingService = newRunningMatchingService(
                matchingPolicyPropertiesWithCooldown(1, dreamiExpirationCooldown));
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.registerDreami(dreamiId, location);
        matchingService.startMatching(orderMock(orderId));

        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> !group.offers().isEmpty())
                .orElse(false), Duration.ofSeconds(2));

        MatchOffer offer1 = matchingService.findOrderOfferGroup(orderId).orElseThrow().offers().getFirst();

        // when (드리미 응답 timeout 발생 - 쿨다운 카운트가 시작된다)
        matchingService.expireDreamiOffer(offer1.offerId());
        awaitUntil(() -> offer1.status() == MatchOfferStatus.DREAMI_EXPIRED, Duration.ofSeconds(2));

        // then (쿨다운이 끝나기 전에는 배치가 여러 번 흘러가도 같은 드리미가 재제안되지 않는다)
        sleep(dreamiExpirationCooldown.dividedBy(2));
        OrderOfferGroup groupDuringCooldown = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(groupDuringCooldown.offers()).hasSize(1);
        assertThat(groupDuringCooldown.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        // 만료된 오퍼는 이력으로 남지만, 신규 pending 오퍼 조회에는 잡히지 않는다.
        assertThat(matchingService.findPendingOfferForDreami(dreamiId)).isEmpty();
        assertThat(matchingService.waitingDreamis())
                .filteredOn(dreami -> dreami.dreamiId().equals(dreamiId))
                .extracting(WaitingDreami::status)
                .containsExactly(WaitingDreamiStatus.MATCHING);

        // when (쿨다운이 끝난 뒤에는 다음 배치에서 같은 드리미에게 새 오퍼가 생성된다)
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> group.offers().size() == 2)
                .orElse(false), Duration.ofSeconds(2));

        // then
        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        MatchOffer offer2 = group.offers().getLast();

        assertThat(offer1.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);
        assertThat(offer2.offerId()).isNotEqualTo(offer1.offerId());
        assertThat(offer2.dreamiId()).isEqualTo(dreamiId);
        assertThat(offer2.status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(matchingService.findPendingOfferForDreami(dreamiId)).contains(offer2);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("대기 중 인터럽트되었습니다.");
        }
    }

    private void awaitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(Duration.ofMillis(5));
        }
        fail("조건이 제한 시간 내에 충족되지 않았습니다.");
    }
}
