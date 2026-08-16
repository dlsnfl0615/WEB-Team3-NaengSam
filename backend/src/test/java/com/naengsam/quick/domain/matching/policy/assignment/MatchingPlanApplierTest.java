package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.global.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingPlanApplier가 검증된 MatchingPlan을 엔진 상태(OrderOfferGroup/WaitingDreami와 등록 맵)에
 * 올바르게 반영하고, 잘못된 plan은 어떤 상태도 바꾸지 않은 채 거부하는지 확인한다.
 */
class MatchingPlanApplierTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 10, 12, 0);
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 8, 10, 12, 0, 5);
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);

    private MatchingService matchingService;
    private NotificationService notificationService;
    private OrderService orderService;
    private MatchingPlanApplier applier;

    private Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId;
    private Map<UUID, WaitingDreami> dreamiMap;
    private Map<UUID, MatchOffer> offersById;
    private Map<UUID, Set<UUID>> offerIdsByDreamiId;

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        notificationService = mock(NotificationService.class);
        orderService = mock(OrderService.class);
        // 이 파일 대부분의 테스트는 오퍼 생성 로직 자체를 검증하므로, 오퍼 직전 DB 상태 확인(가드)이 항상
        // MATCHING을 반환해 통과하도록 기본값을 둔다. 가드는 findOrders(orderId 목록)를 한 번에 호출하므로,
        // 요청받은 orderId 전부를 MATCHING으로 응답한다. 가드 자체를 검증하는 테스트는 개별적으로 재스텁한다.
        Orders matchingOrder = mock(Orders.class);
        lenient().when(matchingOrder.getOrderCd()).thenReturn(OrderCd.MATCHING);
        lenient().when(orderService.findOrders(any())).thenAnswer(invocation -> {
            Collection<UUID> orderIds = invocation.getArgument(0);
            return orderIds.stream().collect(Collectors.toMap(id -> id, id -> matchingOrder));
        });
        applier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), matchingService,
                notificationService, OFFER_TTL, orderService);

        orderOfferGroupsByOrderId = new HashMap<>();
        dreamiMap = new HashMap<>();
        offersById = new HashMap<>();
        offerIdsByDreamiId = new HashMap<>();
    }

    @Test
    void 한_주문에_여러_오퍼를_생성한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreami1);
        registerDreami(dreami2);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreami1, dreami2), List.of(orderId), List.of(dreami1, dreami2));
        MatchingPlan plan = new MatchingPlan(List.of(
                new MatchingProposal(orderId, dreami1, snapshot()), new MatchingProposal(orderId, dreami2, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.offers()).hasSize(2);
        assertThat(group.offers()).allMatch(offer -> offer.status() == MatchOfferStatus.OFFERED);
        assertThat(offersById).hasSize(2);
    }

    @Test
    void maxConcurrentOffers보다_적은_제안도_허용된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreami1);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreami1), List.of(orderId), List.of(dreami1));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreami1, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.offers()).hasSize(1);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void 여러_주문에_계획대로_오퍼를_생성하고_제안없는_주문은_WAITING을_유지한다() {
        UUID orderWithProposal = UUID.randomUUID();
        UUID orderWithoutProposal = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        OrderOfferGroup groupWithProposal = registerGroup(orderWithProposal);
        OrderOfferGroup groupWithoutProposal = registerGroup(orderWithoutProposal);
        registerDreami(dreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderWithProposal, 3), orderInput(orderWithoutProposal, 3)),
                List.of(dreamiId), List.of(orderWithProposal, orderWithoutProposal), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderWithProposal, dreamiId, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(groupWithProposal.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(groupWithoutProposal.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(groupWithoutProposal.offers()).isEmpty();
    }

    @Test
    void 빈_plan은_상태를_변경하지_않는다() {
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(), List.of(orderId), List.of());
        MatchingPlan plan = new MatchingPlan(List.of());

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.offers()).isEmpty();
        verifyNoInteractions(matchingService, notificationService);
    }

    @Test
    void timeout과_SSE가_proposal_수만큼_실행된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();
        registerGroup(orderId);
        registerDreami(dreami1);
        registerDreami(dreami2);
        registerDreami(dreami3);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreami1, dreami2, dreami3),
                List.of(orderId), List.of(dreami1, dreami2, dreami3));
        MatchingPlan plan = new MatchingPlan(List.of(
                new MatchingProposal(orderId, dreami1, snapshot()),
                new MatchingProposal(orderId, dreami2, snapshot()),
                new MatchingProposal(orderId, dreami3, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        verify(matchingService, times(3)).scheduleDreamiOfferTimeout(any(), eq(OFFER_TTL));
        verify(notificationService, times(3)).notify(any(), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 잘못된_plan은_상태_변경_전에_거부된다() {
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(), List.of());
        // 문제에 존재하지 않는 orderId에 대한 제안 -> validator가 거부해야 한다.
        MatchingPlan plan = new MatchingPlan(
                List.of(new MatchingProposal(UUID.randomUUID(), UUID.randomUUID(), snapshot())));

        Throwable thrown = catchThrowable(() -> applier.apply(
                problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(offersById).isEmpty();
        verifyNoInteractions(matchingService, notificationService);
    }

    @Test
    void proposal_적용_시_생성_시각을_저장한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 1)), List.of(dreamiId), List.of(orderId), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.offers()).singleElement()
                .extracting(MatchOffer::statusUpdatedAt).isEqualTo(APPLIED_AT);
    }

    @Test
    void 살아있는_오퍼가_있는_그룹에는_추가_오퍼를_생성하지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID existingDreamiId = UUID.randomUUID();
        UUID newDreamiId = UUID.randomUUID();
        List<MatchOffer> existingOffers = new ArrayList<>(List.of(new MatchOffer(
                UUID.randomUUID(), orderId, existingDreamiId, MatchOfferStatus.OFFERED, EVALUATED_AT)));
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), LOCATION, mock(OrderSummaryDto.class), List.of(),
                EVALUATED_AT.minusMinutes(10));
        group.addOffersAndOpen(existingOffers);
        orderOfferGroupsByOrderId.put(orderId, group);
        registerDreami(newDreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(newDreamiId), List.of(orderId), List.of(newDreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, newDreamiId, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.offers()).hasSize(1);
        verifyNoInteractions(matchingService, notificationService);
    }

    @Test
    void DB_주문이_MATCHING이_아니면_배치_오퍼를_생성하지_않는다() {
        // given (스냅샷 조립~계획 적용 사이 주문이 취소된 경우 - 그룹은 아직 살아있는 오퍼 없이 WAITING이다)
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreamiId);
        Orders cancelledOrder = mock(Orders.class);
        when(cancelledOrder.getOrderCd()).thenReturn(OrderCd.CANCELLED);
        // doReturn (when이 아님): setUp의 기본 답변형(thenAnswer) 스텁이 이미 있는 상태에서 when(mock.foo(any()))으로
        // 재스텁하면, 레코딩 과정에서 그 기본 답변이 null 인자로 실행돼 NPE가 난다. doReturn은 기존 스텁을 실행하지
        // 않고 바로 덮어쓴다.
        doReturn(Map.of(orderId, cancelledOrder)).when(orderService).findOrders(any());
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreamiId), List.of(orderId), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        // when
        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        // then
        assertThat(group.offers()).isEmpty();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(dreamiMap.get(dreamiId).status()).isEqualTo(WaitingDreamiStatus.MATCHING);
        verifyNoInteractions(matchingService, notificationService);
    }

    @Test
    void DB에_주문이_없으면_배치_오퍼를_생성하지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreamiId);
        doReturn(Map.of()).when(orderService).findOrders(any());
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreamiId), List.of(orderId), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        // when
        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        // then
        assertThat(group.offers()).isEmpty();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(dreamiMap.get(dreamiId).status()).isEqualTo(WaitingDreamiStatus.MATCHING);
        verifyNoInteractions(matchingService, notificationService);
    }

    @Test
    void 이전에_DREAMI_EXPIRED로_종료된_같은_주문_드리미_조합에_재제안하면_새_UUID의_오퍼가_생성된다() {
        // 쿨다운이 끝나 같은 (orderId, dreamiId) 조합이 다시 적격 판정을 받았을 때, MatchingPlanApplier가 만드는 새 오퍼는
        // 이전 만료 오퍼와 다른 offerId를 가져야 한다 — 재사용/덮어쓰기가 아니라 항상 새 UUID로 생성됨을 확인한다.
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer expiredOffer = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.DREAMI_EXPIRED, EVALUATED_AT.minusMinutes(10));
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), LOCATION, mock(OrderSummaryDto.class),
                new ArrayList<>(List.of(expiredOffer)), EVALUATED_AT.minusMinutes(20));
        orderOfferGroupsByOrderId.put(orderId, group);
        registerDreami(dreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(dreamiId), List.of(orderId), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.offers()).hasSize(2);
        MatchOffer newOffer = group.offers().getLast();
        assertThat(newOffer.offerId()).isNotEqualTo(expiredOffer.offerId());
        assertThat(newOffer.dreamiId()).isEqualTo(dreamiId);
        assertThat(newOffer.status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    private OrderOfferGroup registerGroup(UUID orderId) {
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), LOCATION, mock(OrderSummaryDto.class), List.of(),
                EVALUATED_AT.minusMinutes(10));
        orderOfferGroupsByOrderId.put(orderId, group);
        return group;
    }

    private void registerDreami(UUID dreamiId) {
        dreamiMap.put(dreamiId, new WaitingDreami(
                dreamiId, LOCATION, WaitingDreamiStatus.MATCHING, EVALUATED_AT.minusMinutes(5)));
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }

    private OfferPolicySnapshot snapshot() {
        return new OfferPolicySnapshot(Duration.ZERO, EVALUATED_AT, 0L, 0.0, 3_000);
    }

    private MatchingAssignmentProblem problem(
            List<MatchingOrderInput> orders, List<UUID> dreamiIds,
            List<UUID> candidateOrderIds, List<UUID> candidateDreamiIds
    ) {
        List<MatchingDreamiInput> dreamis = dreamiIds.stream()
                .map(dreamiId -> new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO))
                .toList();

        List<MatchingCandidate> candidates = new ArrayList<>();
        for (UUID orderId : candidateOrderIds) {
            for (UUID dreamiId : candidateDreamiIds) {
                candidates.add(new MatchingCandidate(
                        orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0, Optional.empty()));
            }
        }

        return new MatchingAssignmentProblem(EVALUATED_AT, orders, dreamis, candidates);
    }
}
