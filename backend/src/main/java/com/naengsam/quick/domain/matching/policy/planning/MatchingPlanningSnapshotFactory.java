package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingDreamiInput;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingOrderInput;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.scope.OfferScope;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 평가 시각·대상 상태·대기시간·quota·이전 오퍼 이력을 cycle당 한 번만 계산한다. */
@Component
@RequiredArgsConstructor
public class MatchingPlanningSnapshotFactory {

    private static final int MIN_OFFER_COUNT = 1;

    private final MatchingPolicyProperties matchingPolicyProperties;
    private final Clock clock;
    private final OfferScopeResolver offerScopeResolver;

    public MatchingPlanningSnapshot create(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    ) {
        LocalDateTime evaluatedAt = LocalDateTime.now(clock);
        List<OrderOfferGroup> waitingGroups = orderOfferGroups.stream()
                .filter(group -> group.status() == OrderOfferGroupStatus.WAITING)
                .toList();
        List<WaitingDreami> matchingDreamis = waitingDreamis.stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .toList();

        int maxConcurrentOffers = resolveMaxConcurrentOffers(waitingGroups.size(), matchingDreamis.size());
        List<MatchingOrderInput> orders = new ArrayList<>(waitingGroups.size());
        List<OfferScope> offerScopes = new ArrayList<>(waitingGroups.size());
        List<Duration> offerScopeKeys = new ArrayList<>(waitingGroups.size());
        List<Map<UUID, PreviousOfferInteraction>> previousInteractions = new ArrayList<>(waitingGroups.size());

        for (OrderOfferGroup group : waitingGroups) {
            MatchingOrderInput order = MatchingOrderInput.from(group, evaluatedAt, maxConcurrentOffers);
            orders.add(order);
            offerScopes.add(offerScopeResolver.resolve(order.waitingTime()));
            offerScopeKeys.add(offerScopeResolver.resolveScopeKey(order.waitingTime()));
            previousInteractions.add(indexPreviousInteractions(group));
        }

        List<MatchingDreamiInput> dreamis = matchingDreamis.stream()
                .map(dreami -> new MatchingDreamiInput(
                        dreami.dreamiId(), dreami.location(), Duration.between(dreami.updatedAt(), evaluatedAt)))
                .toList();

        return new MatchingPlanningSnapshot(
                evaluatedAt,
                waitingGroups,
                matchingDreamis,
                List.copyOf(orders),
                dreamis,
                List.copyOf(offerScopes),
                List.copyOf(offerScopeKeys),
                List.copyOf(previousInteractions));
    }

    private Map<UUID, PreviousOfferInteraction> indexPreviousInteractions(OrderOfferGroup group) {
        Map<UUID, PreviousOfferInteraction> result = new HashMap<>();
        for (MatchOffer offer : group.offers()) {
            Optional<PreviousOfferOutcome> outcome = toOutcome(offer.status());
            if (outcome.isEmpty()) {
                continue;
            }
            PreviousOfferInteraction interaction =
                    new PreviousOfferInteraction(outcome.orElseThrow(), offer.statusUpdatedAt());
            PreviousOfferInteraction current = result.get(offer.dreamiId());
            if (current == null || interaction.occurredAt().isAfter(current.occurredAt())) {
                result.put(offer.dreamiId(), interaction);
            }
        }
        return Map.copyOf(result);
    }

    private Optional<PreviousOfferOutcome> toOutcome(MatchOfferStatus status) {
        return switch (status) {
            case DREAMI_REJECTED -> Optional.of(PreviousOfferOutcome.DREAMI_REJECTED);
            case BOORMI_REJECTED -> Optional.of(PreviousOfferOutcome.BOORMI_REJECTED);
            case DREAMI_EXPIRED -> Optional.of(PreviousOfferOutcome.DREAMI_EXPIRED);
            case BOORMI_EXPIRED -> Optional.of(PreviousOfferOutcome.BOORMI_EXPIRED);
            case WITHDRAWN -> Optional.of(PreviousOfferOutcome.WITHDRAWN);
            case OFFERED, PENDING_BOORMI_CONFIRMATION, MATCHED -> Optional.empty();
        };
    }

    private int resolveMaxConcurrentOffers(int orderCount, int dreamiCount) {
        return switch (matchingPolicyProperties.offerQuotaMode()) {
            case FIXED -> matchingPolicyProperties.maxConcurrentOffers();
            case DYNAMIC -> calculateDynamicQuota(orderCount, dreamiCount);
        };
    }

    private int calculateDynamicQuota(int orderCount, int dreamiCount) {
        if (orderCount == 0) {
            return MIN_OFFER_COUNT;
        }
        int quota = Math.ceilDiv(dreamiCount, orderCount);
        return Math.clamp(quota, MIN_OFFER_COUNT, matchingPolicyProperties.dynamicQuotaMax());
    }
}
