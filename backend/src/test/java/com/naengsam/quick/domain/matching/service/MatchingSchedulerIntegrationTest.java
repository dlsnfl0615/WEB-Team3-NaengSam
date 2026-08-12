package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.scheduler.MatchingScheduler;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.sse.SseService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingEngine/MatchingActionScheduler가 하나의 MatchingScheduler로 합쳐진 뒤에도, 배치 window(마이크로배치)와 오퍼 timeout이 실제 스케줄러 위에서 지연
 * 순서대로(배치가 먼저, timeout이 나중에) 올바르게 실행되는지 검증한다. Mock이 아닌 실제
 * MatchingScheduler/MatchingBatchDispatcher/MatchingPlanApplier를 조합해, 두 종류의 지연 Action이 같은 워커에서 경합 없이 처리되는지 확인한다.
 */
class MatchingSchedulerIntegrationTest {

    private static final Duration BATCH_WINDOW = Duration.ofMillis(50);
    private static final Duration OFFER_TTL = Duration.ofMillis(150);

    private MatchingScheduler matchingScheduler;
    private MatchingService matchingService;

    private static Orders orderMock(UUID orderId) {
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        return order;
    }

    @BeforeEach
    void setUp() {
        matchingScheduler = new MatchingScheduler();
        matchingScheduler.start();

        SseService sseService = mock(SseService.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        Clock clock = Clock.systemDefaultZone();

        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(500.0);

        MatchingPolicyProperties properties = new MatchingPolicyProperties(
                BATCH_WINDOW, 3,
                AssignmentPolicyType.LEGACY_ORDER_FIRST, ScoringPolicyType.ORDER_WAIT, EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10),
                        Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
        MatchingAssignmentPolicy assignmentPolicy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());
        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, new MatchingAssignmentProblemFactory(new LegacyOfferPolicy()),
                properties, clock);
        MatchingBatchDispatcher matchingBatchDispatcher =
                new MatchingBatchDispatcher(matchingScheduler, properties, null);

        matchingService = new MatchingService(
                matchingScheduler, sseService, matchingBatchDispatcher, deliveryService, clock,
                assembler, assignmentPolicy, null, properties, geoDistanceCalculator);
        MatchingPlanApplier matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), matchingService, sseService, OFFER_TTL);
        setPlanApplier(matchingBatchDispatcher, matchingService, matchingPlanApplier);
    }

    // MatchingBatchDispatcher/MatchingService/MatchingPlanApplier는 서로가 서로를 참조하는 순환 구조라,
    // 운영 코드에서는 @Lazy 프록시로 끊어내지만 순수 단위 조립에서는 그 자리를 대체할 수 없어 필드를 직접 맞춰 넣는다.
    private void setPlanApplier(
            MatchingBatchDispatcher matchingBatchDispatcher, MatchingService matchingService,
            MatchingPlanApplier matchingPlanApplier) {
        org.springframework.test.util.ReflectionTestUtils.setField(
                matchingBatchDispatcher, "matchingService", matchingService);
        org.springframework.test.util.ReflectionTestUtils.setField(
                matchingService, "matchingPlanApplier", matchingPlanApplier);
    }

    @AfterEach
    void tearDown() {
        matchingScheduler.shutdown();
    }

    @Test
    void 배치_window가_지난_뒤_오퍼가_생성되고_그보다_늦게_timeout이_실행되어_만료된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = orderMock(orderId);

        matchingService.registerDreami(dreamiId, location);
        matchingService.startMatching(order);

        // 배치 window가 지나면 dirty 표시로 예약된 RunMatchingAssignmentCycle이 실행되어 오퍼가 열린다.
        awaitUntil(() -> matchingService.findOrderOfferGroup(orderId)
                .map(group -> !group.offers().isEmpty())
                .orElse(false), Duration.ofSeconds(2));

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        MatchOffer offer = group.offers().getFirst();
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.OFFERED);

        // 배치보다 나중에 만료되도록 예약된 오퍼 timeout이 실행되어 OFFERED였던 오퍼가 DREAMI_EXPIRED로 전이된다.
        awaitUntil(() -> offer.status() == MatchOfferStatus.DREAMI_EXPIRED, Duration.ofSeconds(2));

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);
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
}
