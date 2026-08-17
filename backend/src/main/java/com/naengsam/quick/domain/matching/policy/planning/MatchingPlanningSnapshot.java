package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingDreamiInput;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingOrderInput;
import com.naengsam.quick.domain.matching.policy.scope.OfferScope;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 한 matching cycle에서 두 planning policy가 공통으로 사용하는 입력 스냅샷. */
public record MatchingPlanningSnapshot(
        LocalDateTime evaluatedAt,
        List<OrderOfferGroup> orderOfferGroups,
        List<WaitingDreami> waitingDreamis,
        List<MatchingOrderInput> orders,
        List<MatchingDreamiInput> dreamis,
        List<OfferScope> offerScopes,
        List<Duration> offerScopeKeys,
        List<Map<UUID, PreviousOfferInteraction>> previousInteractionsByOrder
) {
}
