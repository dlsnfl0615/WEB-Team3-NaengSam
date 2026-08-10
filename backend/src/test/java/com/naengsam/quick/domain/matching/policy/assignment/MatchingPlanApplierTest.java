package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import com.naengsam.quick.domain.matching.service.OfferTimeoutScheduler;
import com.naengsam.quick.global.sse.SseService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private OfferTimeoutScheduler offerTimeoutScheduler;
    private SseService sseService;
    private MatchingPlanApplier applier;

    private Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId;
    private Map<UUID, WaitingDreami> dreamiMap;
    private Map<UUID, MatchOffer> offersById;
    private Map<UUID, Set<UUID>> offerIdsByDreamiId;

    @BeforeEach
    void setUp() {
        offerTimeoutScheduler = mock(OfferTimeoutScheduler.class);
        sseService = mock(SseService.class);
        applier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), offerTimeoutScheduler, sseService, OFFER_TTL);

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
                new MatchingProposal(orderId, dreami1), new MatchingProposal(orderId, dreami2)));

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
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreami1)));

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
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderWithProposal, dreamiId)));

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
        verifyNoInteractions(offerTimeoutScheduler, sseService);
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
                new MatchingProposal(orderId, dreami1),
                new MatchingProposal(orderId, dreami2),
                new MatchingProposal(orderId, dreami3)));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        verify(offerTimeoutScheduler, times(3)).scheduleDreamiOfferTimeout(any(), eq(OFFER_TTL));
        verify(sseService, times(3)).send(any(), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 잘못된_plan은_상태_변경_전에_거부된다() {
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(), List.of());
        // 문제에 존재하지 않는 orderId에 대한 제안 -> validator가 거부해야 한다.
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(UUID.randomUUID(), UUID.randomUUID())));

        Throwable thrown = catchThrowable(() -> applier.apply(
                problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(offersById).isEmpty();
        verifyNoInteractions(offerTimeoutScheduler, sseService);
    }

    @Test
    void proposal_적용_시_생성_시각을_저장한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        OrderOfferGroup group = registerGroup(orderId);
        registerDreami(dreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 1)), List.of(dreamiId), List.of(orderId), List.of(dreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId)));

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
                orderId, UUID.randomUUID(), LOCATION, List.of(), EVALUATED_AT.minusMinutes(10));
        group.addOffersAndOpen(existingOffers);
        orderOfferGroupsByOrderId.put(orderId, group);
        registerDreami(newDreamiId);
        MatchingAssignmentProblem problem = problem(
                List.of(orderInput(orderId, 3)), List.of(newDreamiId), List.of(orderId), List.of(newDreamiId));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, newDreamiId)));

        applier.apply(problem, plan, APPLIED_AT, orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);

        assertThat(group.offers()).hasSize(1);
        verifyNoInteractions(offerTimeoutScheduler, sseService);
    }

    private OrderOfferGroup registerGroup(UUID orderId) {
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), LOCATION, List.of(), EVALUATED_AT.minusMinutes(10));
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
