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
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.sse.SseService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 최초 매칭 시작(applyStartMatching)이 즉시 오퍼를 만들지 않고 배치 매칭 사이클(applyRunMatchingAssignmentCycle)로 위임되는 흐름을, 실제
 * MatchingAssignmentProblemAssembler/MatchingAssignmentPolicy/MatchingPlanApplier를 조합해 end-to-end로 검증한다. 오퍼 timeout
 * 스케줄링·SSE 발송·배치 스케줄러 자체 같은 외부 부수효과만 mock으로 대체하고, 배치 사이클은 실제 스케줄러를 기다리지 않고
 * {@link MatchingService#applyRunMatchingAssignmentCycle()}을 직접 호출해 트리거한다.
 */
class MatchingMicroBatchIntegrationTest {

    private static final Duration OFFER_TTL = Duration.ofSeconds(30);

    private static MatchingPolicyProperties matchingPolicyProperties(int maxConcurrentOffers) {
        return new MatchingPolicyProperties(
                Duration.ofMillis(200),
                maxConcurrentOffers,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10),
                        Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    private static Orders orderMock(UUID orderId) {
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        return order;
    }

    private MatchingService newMatchingService(int maxConcurrentOffers) {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        SseService sseService = mock(SseService.class);
        MatchingActionScheduler matchingActionScheduler = mock(MatchingActionScheduler.class);
        MatchingBatchDispatcher matchingBatchDispatcher = mock(MatchingBatchDispatcher.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        Clock clock = Clock.systemDefaultZone();

        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(500.0);

        MatchingPolicyProperties properties = matchingPolicyProperties(maxConcurrentOffers);
        MatchingAssignmentPolicy assignmentPolicy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());
        MatchingPlanApplier matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), matchingActionScheduler, sseService, OFFER_TTL);

        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, new MatchingAssignmentProblemFactory(new LegacyOfferPolicy()),
                properties, clock);

        return new MatchingService(
                matchingEngine, sseService, matchingActionScheduler, matchingBatchDispatcher, deliveryService,
                clock,
                assembler, assignmentPolicy, matchingPlanApplier, properties, geoDistanceCalculator);
    }

    @Test
    void 주문_등록_직후에는_오퍼가_생성되지_않는다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderId = UUID.randomUUID();

        matchingService.applyStartMatching(orderMock(orderId));

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.offers()).isEmpty();
    }

    @Test
    void 배치_사이클_실행_후_대기중인_드리미에게_오퍼가_생성된다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderId));

        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.offers()).hasSize(1);
        assertThat(group.offers().getFirst().status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
    }

    @Test
    void 같은_윈도우에_등록된_여러_주문이_하나의_배치에서_함께_평가된다() {
        MatchingService matchingService = newMatchingService(1);
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        UUID orderId3 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.applyStartMatching(orderMock(orderId1));
        matchingService.applyStartMatching(orderMock(orderId2));
        matchingService.applyStartMatching(orderMock(orderId3));

        // 배치 실행 전에는 세 그룹 다 WAITING, 오퍼 없음
        assertThat(matchingService.orderOfferGroups())
                .allMatch(group -> group.status() == OrderOfferGroupStatus.WAITING && group.offers().isEmpty());

        matchingService.applyRunMatchingAssignmentCycle();

        // 한 번의 배치 사이클로 세 주문 모두 오퍼를 받는다(주문별 maxConcurrentOffers=1이므로 정확히 1건씩, 서로 다른 드리미에게)
        assertThat(matchingService.orderOfferGroups())
                .hasSize(3)
                .allMatch(group -> group.status() == OrderOfferGroupStatus.OPEN)
                .allMatch(group -> group.offers().size() == 1);
    }

    @Test
    void 같은_윈도우에_등록된_여러_드리미가_하나의_배치_문제에_포함된다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(orderMock(orderId));

        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.offers()).hasSize(3);
        assertThat(group.offers()).allMatch(offer -> offer.status() == MatchOfferStatus.OFFERED);
    }

    @Test
    void 배치_실행_전에_취소된_주문은_배치에서_제외된다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderId));
        matchingService.applyCancelOrderByBoormi(orderId);

        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.offers()).isEmpty();
    }

    @Test
    void 이미_진행중인_방이_있으면_중복으로_매칭이_시작되지_않는다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderId = UUID.randomUUID();
        Orders order = orderMock(orderId);

        matchingService.applyStartMatching(order);
        OrderOfferGroup originalGroup = matchingService.findOrderOfferGroup(orderId).orElseThrow();

        boolean started = matchingService.startMatching(order);

        assertThat(started).isFalse();
        assertThat(matchingService.findOrderOfferGroup(orderId).orElseThrow()).isSameAs(originalGroup);
    }

    @Test
    void 주문별_maxConcurrentOffers를_초과해서_오퍼가_생성되지_않는다() {
        MatchingService matchingService = newMatchingService(2);
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(orderMock(orderId));

        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = matchingService.findOrderOfferGroup(orderId).orElseThrow();
        assertThat(group.offers()).hasSize(2);
    }

    // ────────────────────────────── 회수된 드리미가 배치 디스패처를 통해 다른 대기 주문에 재배정되는지 검증 ──────────────────────────────

    @Test
    void 핵심_시나리오_드리미가_거절한_주문에서_풀려나면_다음_배치에서_다른_대기_주문에_배정된다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offerA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow().offers().getFirst();
        assertThat(offerA.dreamiId()).isEqualTo(dreamiId);

        matchingService.applyStartMatching(orderMock(orderIdB)); // B는 아직 배치를 못 받아 WAITING

        // when (D가 A를 거절 -> MATCHING으로 복귀, 다음 배치에서 B와 함께 평가된다)
        matchingService.applyRejectByDreami(offerA.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then (같은 드리미 재제안 금지 정책 때문에 D는 A로는 못 돌아가지만 B에는 배정된다
        // — A에 대한 이전 거절 이력이 B 후보 평가에는 영향을 주지 않는다)
        OrderOfferGroup groupA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();

        assertThat(groupA.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(groupA.offers()).noneMatch(offer -> offer.status() == MatchOfferStatus.OFFERED);

        assertThat(groupB.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupB.offers()).hasSize(1);
        assertThat(groupB.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
        assertThat(groupB.offers().getFirst().status()).isEqualTo(MatchOfferStatus.OFFERED);
    }

    @Test
    void A의_다른_오퍼가_살아있어도_거절한_드리미는_B로_이동한다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle(); // A는 두 드리미 모두에게 오퍼

        OrderOfferGroup groupABefore = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        assertThat(groupABefore.offers()).hasSize(2);
        MatchOffer offerToReject = groupABefore.offers().stream()
                .filter(offer -> offer.dreamiId().equals(dreamiId1))
                .findFirst().orElseThrow();

        matchingService.applyStartMatching(orderMock(orderIdB));

        // when (dreamiId1만 거절 -> dreamiId2의 오퍼는 여전히 살아있어 A는 OPEN을 유지한다)
        matchingService.applyRejectByDreami(offerToReject.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then (A가 OPEN을 유지해도 거절한 dreamiId1은 B로 이동한다)
        OrderOfferGroup groupA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();

        assertThat(groupA.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupA.offers())
                .filteredOn(offer -> offer.dreamiId().equals(dreamiId2))
                .extracting(MatchOffer::status)
                .containsExactly(MatchOfferStatus.OFFERED);

        assertThat(groupB.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupB.offers()).hasSize(1);
        assertThat(groupB.offers().getFirst().dreamiId()).isEqualTo(dreamiId1);
    }

    @Test
    void 선착순_마감으로_회수된_드리미가_다른_대기_주문에_배정된다() {
        MatchingService matchingService = newMatchingService(2);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle(); // maxConcurrentOffers=2 -> 둘 다 오퍼

        OrderOfferGroup groupABefore = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        assertThat(groupABefore.offers()).hasSize(2);
        MatchOffer acceptedOffer = groupABefore.offers().getFirst();
        UUID loserDreamiId = groupABefore.offers().get(1).dreamiId();

        matchingService.applyStartMatching(orderMock(orderIdB));

        // when (한 명이 수락 -> 나머지 한 명은 WITHDRAWN되어 회수된다)
        matchingService.applyAcceptByDreami(acceptedOffer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then (회수된 드리미가 B에 배정된다)
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();
        assertThat(groupB.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupB.offers()).hasSize(1);
        assertThat(groupB.offers().getFirst().dreamiId()).isEqualTo(loserDreamiId);
    }

    @Test
    void 주문_취소로_반환된_드리미가_다른_대기_주문에_배정된다() {
        MatchingService matchingService = newMatchingService(3);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle();
        assertThat(matchingService.findOrderOfferGroup(orderIdA).orElseThrow().offers()).hasSize(1);

        matchingService.applyStartMatching(orderMock(orderIdB));

        // when (A가 취소되어 드리미가 반환된다)
        matchingService.applyCancelOrderByBoormi(orderIdA);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup groupA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();

        assertThat(groupA.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(groupB.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupB.offers()).hasSize(1);
        assertThat(groupB.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
    }

    @Test
    void 여러_상태_변경이_하나의_배치로_병합되어_반영된다() {
        MatchingService matchingService = newMatchingService(1);
        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        UUID orderIdC = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderMock(orderIdA));
        matchingService.applyRunMatchingAssignmentCycle(); // A-dreami 오퍼

        MatchOffer offerA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow().offers().getFirst();

        // when (거절과 두 개의 새 주문 시작을 모두 같은 배치 실행 전에 쌓아둔다)
        matchingService.applyRejectByDreami(offerA.offerId());
        matchingService.applyStartMatching(orderMock(orderIdB));
        matchingService.applyStartMatching(orderMock(orderIdC));

        matchingService.applyRunMatchingAssignmentCycle();

        // then (한 번의 배치 실행으로 세 주문 모두 함께 평가된다: A는 같은 드리미에게 재제안되지 않아 WAITING을 유지하고,
        // B/C 중 드리미를 받은 쪽만 OPEN이 된다)
        OrderOfferGroup groupA = matchingService.findOrderOfferGroup(orderIdA).orElseThrow();
        OrderOfferGroup groupB = matchingService.findOrderOfferGroup(orderIdB).orElseThrow();
        OrderOfferGroup groupC = matchingService.findOrderOfferGroup(orderIdC).orElseThrow();

        assertThat(groupA.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(groupA.offers()).noneMatch(offer -> offer.status() == MatchOfferStatus.OFFERED);

        long openCount = Stream.of(groupB, groupC)
                .filter(group -> group.status() == OrderOfferGroupStatus.OPEN)
                .count();
        assertThat(openCount).isEqualTo(1);
    }
}
