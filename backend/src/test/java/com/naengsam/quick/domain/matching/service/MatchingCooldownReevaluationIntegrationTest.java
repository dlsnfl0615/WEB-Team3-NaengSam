package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.notification.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 쿨다운(OUTCOME_COOLDOWN) 기반 적격성 정책이 반복 배치 사이클을 거치며 실제로 재평가되는지 검증한다. 시간 경과는 실제 sleep이 아니라
 * {@link MutableClock}으로 직접 흘려보내, 쿨다운 만료 전/후를 결정적으로 재현한다. 배치 사이클 자체는
 * {@link MatchingService#applyRunMatchingAssignmentCycle()}을 직접 호출해 트리거한다(주기적 실행 자체는
 * {@link PeriodicMatchingBatchIntegrationTest}에서 별도로 검증한다).
 */
class MatchingCooldownReevaluationIntegrationTest {

    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    private static final Duration DREAMI_REJECTION_COOLDOWN = Duration.ofMinutes(10);
    private static final Duration BOORMI_REJECTION_COOLDOWN = Duration.ofMinutes(15);
    private static final Duration DREAMI_EXPIRATION_COOLDOWN = Duration.ofMinutes(5);

    private static Orders orderMock(UUID orderId) {
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        return order;
    }

    private static MatchingPolicyProperties matchingPolicyProperties(int maxConcurrentOffers) {
        MatchingPolicyProperties.Cooldown cooldown = new MatchingPolicyProperties.Cooldown(
                DREAMI_REJECTION_COOLDOWN, BOORMI_REJECTION_COOLDOWN, DREAMI_EXPIRATION_COOLDOWN);
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                maxConcurrentOffers,
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.OUTCOME_COOLDOWN,
                cooldown,
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    private MatchingService newMatchingService(int maxConcurrentOffers, MutableClock clock) {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.isReachableNow(any())).thenReturn(true);
        DeliveryService deliveryService = mock(DeliveryService.class);

        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(500.0);

        MatchingPolicyProperties properties = matchingPolicyProperties(maxConcurrentOffers);
        MatchingPolicyProperties.Cooldown cooldown = properties.cooldown();
        MatchingEligibilityPolicy eligibilityPolicy = new OutcomeCooldownOfferPolicy(
                cooldown.dreamiRejection(), cooldown.boormiRejection(), cooldown.dreamiExpiration());
        MatchingAssignmentPolicy assignmentPolicy = new ScoreBasedGreedyAssignmentPolicy(new OrderWaitScorePolicy());
        MatchingPlanApplier matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(eligibilityPolicy), mock(MatchingService.class),
                notificationService, OFFER_TTL);

        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, new MatchingAssignmentProblemFactory(eligibilityPolicy),
                properties, clock);

        return new MatchingService(
                matchingEngine, notificationService, deliveryService,
                clock,
                assembler, assignmentPolicy, matchingPlanApplier, properties, geoDistanceCalculator,
                new SimpleMeterRegistry());
    }

    @Test
    void 드리미가_거절한_경우_쿨다운_동안_후보에서_제외되고_쿨다운_만료_후_다시_후보에_포함된다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(1, clock);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderId));
        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.offers()).hasSize(1);
        MatchOffer firstOffer = group.offers().getFirst();
        assertThat(firstOffer.status()).isEqualTo(MatchOfferStatus.OFFERED);

        // when (드리미 거절 - 별도 signal 없이 오퍼 상태만 바뀐다)
        matchingService.applyRejectByDreami(firstOffer.offerId());
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // then (쿨다운이 지나지 않았으므로 다음 배치에서 재후보에 포함되지 않는다)
        matchingService.applyRunMatchingAssignmentCycle();
        assertThat(group.offers()).hasSize(1);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when (쿨다운 만료)
        clock.advance(DREAMI_REJECTION_COOLDOWN);
        matchingService.applyRunMatchingAssignmentCycle();

        // then (다시 후보로 포함되어 재제안된다)
        assertThat(group.offers()).hasSize(2);
        MatchOffer secondOffer = group.offers().get(1);
        assertThat(secondOffer.dreamiId()).isEqualTo(dreamiId);
        assertThat(secondOffer.status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void 쿨다운은_주문_드리미_조합_단위이므로_다른_주문에는_즉시_배정될_수_있다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(1, clock);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offerA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow().offers().getFirst();
        matchingService.applyRejectByDreami(offerA.offerId()); // A에 대해서만 쿨다운 시작

        // when (같은 드리미가 클락을 전혀 흘리지 않고 새 주문 B에 즉시 배정될 수 있는지 확인)
        matchingService.applyStartMatching(orderMock(orderIdB));
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup groupA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();
        assertThat(groupA.status()).isEqualTo(OrderOfferGroupStatus.WAITING); // A는 여전히 쿨다운 중
        assertThat(groupA.offers()).hasSize(1);
        assertThat(groupB.status()).isEqualTo(OrderOfferGroupStatus.OPEN); // B는 즉시 배정됨
        assertThat(groupB.offers()).hasSize(1);
        assertThat(groupB.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
    }

    @Test
    void 부르미가_거절한_경우_쿨다운_동안_후보에서_제외되고_쿨다운_만료_후_다시_후보에_포함된다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(1, clock);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderId));
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer = matchingService.findOrderOfferGroup(orderId).orElseThrow().offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());
        matchingService.applyRejectByBoormi(offer.offerId());

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when (쿨다운 이전)
        matchingService.applyRunMatchingAssignmentCycle();
        assertThat(group.offers()).hasSize(1);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when (쿨다운 만료)
        clock.advance(BOORMI_REJECTION_COOLDOWN);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        assertThat(group.offers()).hasSize(2);
        assertThat(group.offers().get(1).status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void 드리미_응답_timeout이_발생한_경우_쿨다운_동안_후보에서_제외되고_쿨다운_만료_후_다시_후보에_포함된다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(1, clock);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderId));
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer = matchingService.findOrderOfferGroup(orderId).orElseThrow().offers().getFirst();
        matchingService.applyExpireDreamiOffer(offer.offerId());

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when (쿨다운 이전)
        matchingService.applyRunMatchingAssignmentCycle();
        assertThat(group.offers()).hasSize(1);

        // when (쿨다운 만료)
        clock.advance(DREAMI_EXPIRATION_COOLDOWN);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        assertThat(group.offers()).hasSize(2);
        assertThat(group.offers().get(1).status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void WITHDRAWN과_BOORMI_EXPIRED_상태의_드리미는_쿨다운_없이_바로_다음_배치에서_후보로_포함된다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(2, clock);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyStartMatching(orderMock(orderId));
        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.offers()).hasSize(2); // maxConcurrentOffers=2 -> 둘 다 오퍼를 받음

        MatchOffer offerToDreami2 = group.offers().stream()
                .filter(offer -> offer.dreamiId().equals(dreamiId2))
                .findFirst().orElseThrow();

        // dreami2가 수락하면 dreami1의 오퍼는 WITHDRAWN
        matchingService.applyAcceptByDreami(offerToDreami2.offerId());
        MatchOffer withdrawnOffer = group.offers().stream()
                .filter(offer -> offer.dreamiId().equals(dreamiId1))
                .findFirst().orElseThrow();
        assertThat(withdrawnOffer.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);

        // 부르미 확인 응답시간 초과 -> dreami2의 오퍼는 BOORMI_EXPIRED, 그룹은 WAITING으로 복귀
        matchingService.applyExpireBoormiOffer(offerToDreami2.offerId());
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when (클락을 전혀 흘리지 않고 바로 다음 배치를 실행)
        matchingService.applyRunMatchingAssignmentCycle();

        // then (WITHDRAWN/BOORMI_EXPIRED 둘 다 쿨다운 없이 즉시 후보로 포함되어 다시 오퍼를 받는다)
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        List<MatchOffer> liveOffers = group.offers().stream()
                .filter(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .toList();
        assertThat(liveOffers).extracting(MatchOffer::dreamiId).containsExactlyInAnyOrder(dreamiId1, dreamiId2);
    }

    @Test
    void 쿨다운_재평가_중에도_주문별_capacity와_드리미_독점_제약은_유지된다() {
        MutableClock clock = MutableClock.startingNow();
        MatchingService matchingService = newMatchingService(2, clock);
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        UUID dreamiId3 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyRegisterDreami(dreamiId3, location);
        matchingService.applyStartMatching(orderMock(orderId1));
        matchingService.applyStartMatching(orderMock(orderId2));

        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group1 = matchingService.findOrderOfferGroup(orderId1).orElseThrow();
        OrderOfferGroup group2 = matchingService.findOrderOfferGroup(orderId2).orElseThrow();

        // then (주문 하나당 capacity(2)를 넘는 오퍼를 받지 않는다)
        assertThat(group1.offers().size()).isLessThanOrEqualTo(2);
        assertThat(group2.offers().size()).isLessThanOrEqualTo(2);
        // 드리미가 3명뿐이므로 전체 배정 건수도 3건을 넘지 않는다
        assertThat(group1.offers().size() + group2.offers().size()).isEqualTo(3);

        // then (같은 드리미가 서로 다른 두 주문에 동시에 배정되지 않는다 - 독점 제약)
        List<UUID> assignedDreamiIds = Stream.concat(group1.offers().stream(), group2.offers().stream())
                .map(MatchOffer::dreamiId)
                .toList();
        assertThat(assignedDreamiIds).doesNotHaveDuplicates();
    }

    /**
     * 쿨다운 만료 시점을 실제 sleep 없이 결정적으로 재현하기 위한 테스트 전용 가변 Clock.
     */
    private static final class MutableClock extends Clock {

        private volatile Duration offset;
        private final ZoneId zone;

        private MutableClock(Duration offset, ZoneId zone) {
            this.offset = offset;
            this.zone = zone;
        }

        /**
         * {@link WaitingDreami#markProposed()}/{@link WaitingDreami#markMatching()}는 내부적으로 실제 시스템 시각
         * {@code LocalDateTime.now()}로 updatedAt을 갱신한다. 이 가짜 clock을 특정 순간에 고정하면, 테스트 실행 중
         * 흘러가는 실제 시간(수 ms)이 그 고정된 값을 앞질러 evaluatedAt(가짜 clock)이 dreami.updatedAt()(실제 시각)보다
         * 과거가 되는 음수 Duration이 생긴다. 그래서 절대 시각을 고정하는 대신 실제 시각에 누적 offset을 더하는 방식으로,
         * "가짜 지금"이 항상 실제 지금 이상이 되도록 한다({@code advance}는 offset만 늘릴 뿐 절대 감소하지 않는다).
         */
        static MutableClock startingNow() {
            return new MutableClock(Duration.ZERO, ZoneId.systemDefault());
        }

        void advance(Duration duration) {
            offset = offset.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(offset, zone);
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(offset);
        }
    }
}
