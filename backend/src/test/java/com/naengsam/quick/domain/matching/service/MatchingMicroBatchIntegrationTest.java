package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 최초 매칭 시작(applyStartMatching)이 즉시 오퍼를 만들지 않고 배치 매칭 사이클(applyRunMatchingAssignmentCycle)로 위임되는 흐름을, 실제
 * MatchingAssignmentProblemAssembler/MatchingAssignmentPolicy/MatchingPlanApplier를 조합해 end-to-end로 검증한다. 오퍼 timeout
 * 스케줄링·SSE 발송·배치 스케줄러 자체 같은 외부 부수효과만 mock으로 대체하고, 배치 사이클은 실제 스케줄러를 기다리지 않고
 * {@link MatchingService#applyRunMatchingAssignmentCycle()}을 직접 호출해 트리거한다.
 */
class MatchingMicroBatchIntegrationTest {

    private static final Duration OFFER_TTL = Duration.ofSeconds(30);

    private MatchingService newMatchingService(int maxConcurrentOffers) {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        SseService sseService = mock(SseService.class);
        MatchingActionScheduler matchingActionScheduler = mock(MatchingActionScheduler.class);
        MatchingBatchDispatcher matchingBatchDispatcher = mock(MatchingBatchDispatcher.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        Clock clock = Clock.systemDefaultZone();

        BoormiService boormiService = mock(BoormiService.class);
        when(boormiService.distanceMeters(any(), any())).thenReturn(500.0);

        MatchingPolicyProperties properties = matchingPolicyProperties(maxConcurrentOffers);
        MatchingAssignmentPolicy assignmentPolicy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());
        MatchingPlanApplier matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), matchingActionScheduler, sseService, OFFER_TTL);

        MatchingService matchingService = new MatchingService(
                matchingEngine, sseService, matchingActionScheduler, matchingBatchDispatcher, deliveryService, clock,
                mock(MatchingAssignmentProblemAssembler.class), // 순환 의존성 때문에 자리표시자로 생성 후 아래서 실제 객체로 교체
                assignmentPolicy, matchingPlanApplier, properties);

        MatchingAssignmentProblemAssembler realAssembler = new MatchingAssignmentProblemAssembler(
                matchingService, boormiService, new MatchingAssignmentProblemFactory(new LegacyOfferPolicy()),
                properties, clock);
        ReflectionTestUtils.setField(matchingService, "matchingAssignmentProblemAssembler", realAssembler);

        return matchingService;
    }

    private static MatchingPolicyProperties matchingPolicyProperties(int maxConcurrentOffers) {
        return new MatchingPolicyProperties(
                Duration.ofMillis(200),
                maxConcurrentOffers,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    private static Orders orderMock(UUID orderId) {
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        return order;
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
}
