package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.event.OfferPopupPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.global.notification.NotificationService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 검증된 {@link MatchingPlan}을 엔진 상태({@link OrderOfferGroup}/{@link WaitingDreami}와 그 등록 맵)에
 * 반영한다. 같은 orderId의 여러 {@link MatchingProposal}은 하나의 오퍼 라운드로 묶어 한 번에
 * {@link OrderOfferGroup#addOffersAndOpen(List)}을 호출하고, Proposal이 없는 주문은 건드리지 않아 WAITING을
 * 유지한다. {@link MatchingPlanValidator#validate}가 통과해야만 상태 변경을 시작하므로, 잘못된 plan은 어떤
 * 맵/도메인 객체도 건드리지 않은 채 거부된다.
 * <p>각 proposal의 {@link MatchingProposal#offerPolicySnapshot()}은 생성되는 {@link MatchOffer}에 그대로
 * 실린다. 그 뒤 offer-scopes 설정이 바뀌거나 다음 배치에서 더 넓은 scope가 적용돼도, 이미 만들어진 오퍼는 자신이
 * 만들어진 시점의 스냅샷을 그대로 유지한다 — 넓어진 scope는 그 이후 새로 만들어지는 오퍼에만 적용된다.
 */
public class MatchingPlanApplier {

    private final MatchingPlanValidator planValidator;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final Duration offerTtl;
    private final OrderService orderService;

    public MatchingPlanApplier(
            MatchingPlanValidator planValidator,
            MatchingService matchingService,
            NotificationService notificationService,
            Duration offerTtl,
            OrderService orderService
    ) {
        this.planValidator = planValidator;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
        this.offerTtl = offerTtl;
        this.orderService = orderService;
    }

    public void apply(
            MatchingAssignmentProblem problem,
            MatchingPlan plan,
            LocalDateTime appliedAt,
            Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId,
            Map<UUID, WaitingDreami> dreamiMap,
            Map<UUID, MatchOffer> offersById,
            Map<UUID, Set<UUID>> offerIdsByDreamiId
    ) {
        planValidator.validate(problem, plan);

        Map<UUID, List<MatchingProposal>> proposalsByOrderId = plan.proposals().stream()
                .collect(Collectors.groupingBy(MatchingProposal::orderId));

        // 그룹이 없거나 이미 살아있는 오퍼가 있는 주문은 애초에 이번 라운드에서 오퍼를 내보내지 않으므로, DB 조회
        // 대상에서도 미리 제외한다. 남은 후보만 findOrders로 한 번에 조회해, 배치 사이클마다 주문 수만큼
        // 쿼리가 나가는 N+1을 피한다.
        List<UUID> candidateOrderIds = proposalsByOrderId.keySet().stream()
                .filter(orderId -> {
                    OrderOfferGroup group = orderOfferGroupsByOrderId.get(orderId);
                    return group != null && !hasLiveOffer(group);
                })
                .toList();

        // 오퍼를 실제로 내보내기 직전, DB 주문이 그 사이 취소/진행 등으로 바뀌지 않았는지 마지막으로 확인한다.
        // 스냅샷 조립~적용 사이 다른 트랜잭션이 커밋됐을 수 있어, 여기서 걸러야 이미 종료된 주문에 오퍼가 나가지 않는다.
        Map<UUID, Orders> latestOrdersById = orderService.findOrders(candidateOrderIds);

        for (UUID orderId : candidateOrderIds) {
            Orders latestOrder = latestOrdersById.get(orderId);
            if (latestOrder == null || latestOrder.getOrderCd() != OrderCd.MATCHING) {
                continue;
            }
            applyToOrder(orderId, proposalsByOrderId.get(orderId), appliedAt, orderOfferGroupsByOrderId.get(orderId),
                    dreamiMap, offersById, offerIdsByDreamiId);
        }
    }

    private void applyToOrder(
            UUID orderId,
            List<MatchingProposal> proposals,
            LocalDateTime appliedAt,
            OrderOfferGroup group,
            Map<UUID, WaitingDreami> dreamiMap,
            Map<UUID, MatchOffer> offersById,
            Map<UUID, Set<UUID>> offerIdsByDreamiId
    ) {
        List<MatchOffer> newOffers = new ArrayList<>();
        for (MatchingProposal proposal : proposals) {
            WaitingDreami dreami = dreamiMap.get(proposal.dreamiId());
            UUID offerId = UUID.randomUUID();
            MatchOffer offer = new MatchOffer(
                    offerId, orderId, proposal.dreamiId(), MatchOfferStatus.OFFERED, appliedAt,
                    proposal.offerPolicySnapshot());
            newOffers.add(offer);

            offersById.put(offerId, offer);
            offerIdsByDreamiId.computeIfAbsent(proposal.dreamiId(), key -> new HashSet<>()).add(offerId);
            dreami.markProposed();
        }

        group.addOffersAndOpen(newOffers);

        for (MatchOffer offer : newOffers) {
            matchingService.scheduleDreamiOfferTimeout(offer.offerId(), offerTtl);
            notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_POPUP,
                    OfferPopupPayload.from(offer, group.orderSummary(), offerTtl));
        }
    }

    private boolean hasLiveOffer(OrderOfferGroup group) {
        return group.offers().stream()
                .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                        || offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
    }
}
