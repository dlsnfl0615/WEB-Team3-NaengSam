package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.event.OfferPopupPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.service.OfferTimeoutScheduler;
import com.naengsam.quick.global.sse.SseService;
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
 */
public class MatchingPlanApplier {

    private final MatchingPlanValidator planValidator;
    private final OfferTimeoutScheduler offerTimeoutScheduler;
    private final SseService sseService;
    private final Duration offerTtl;

    public MatchingPlanApplier(
            MatchingPlanValidator planValidator,
            OfferTimeoutScheduler offerTimeoutScheduler,
            SseService sseService,
            Duration offerTtl
    ) {
        this.planValidator = planValidator;
        this.offerTimeoutScheduler = offerTimeoutScheduler;
        this.sseService = sseService;
        this.offerTtl = offerTtl;
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

        for (Map.Entry<UUID, List<MatchingProposal>> entry : proposalsByOrderId.entrySet()) {
            applyToOrder(entry.getKey(), entry.getValue(), appliedAt,
                    orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);
        }
    }

    private void applyToOrder(
            UUID orderId,
            List<MatchingProposal> proposals,
            LocalDateTime appliedAt,
            Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId,
            Map<UUID, WaitingDreami> dreamiMap,
            Map<UUID, MatchOffer> offersById,
            Map<UUID, Set<UUID>> offerIdsByDreamiId
    ) {
        OrderOfferGroup group = orderOfferGroupsByOrderId.get(orderId);
        if (group == null || hasLiveOffer(group)) {
            return;
        }

        List<MatchOffer> newOffers = new ArrayList<>();
        for (MatchingProposal proposal : proposals) {
            WaitingDreami dreami = dreamiMap.get(proposal.dreamiId());
            UUID offerId = UUID.randomUUID();
            MatchOffer offer = new MatchOffer(
                    offerId, orderId, proposal.dreamiId(), MatchOfferStatus.OFFERED, appliedAt);
            newOffers.add(offer);

            offersById.put(offerId, offer);
            offerIdsByDreamiId.computeIfAbsent(proposal.dreamiId(), key -> new HashSet<>()).add(offerId);
            dreami.markProposed();
        }

        group.addOffersAndOpen(newOffers);

        for (MatchOffer offer : newOffers) {
            offerTimeoutScheduler.scheduleDreamiOfferTimeout(offer.offerId(), offerTtl);
            sseService.send(offer.dreamiId(), MatchingEventType.OFFER_POPUP,
                    OfferPopupPayload.from(offer, group.orderSummary(), offerTtl));
        }
    }

    private boolean hasLiveOffer(OrderOfferGroup group) {
        return group.offers().stream()
                .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                        || offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
    }
}
