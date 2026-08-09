package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link MatchingPlan}이 원본 {@link MatchingAssignmentProblem}의 제약(주문별 maxConcurrentOffers)을
 * 넘지 않는지, 그리고 각 제안의 후보가 여전히 적격한지 검증한다. MatchingPlan 자체는 각 주문의 한도도, 후보의
 * 적격 여부도 모르므로 문제와 함께 넘겨받아야만 검증할 수 있다.
 */
public class MatchingPlanValidator {

    private final MatchingEligibilityPolicy eligibilityPolicy;

    public MatchingPlanValidator(MatchingEligibilityPolicy eligibilityPolicy) {
        this.eligibilityPolicy = eligibilityPolicy;
    }

    public void validate(MatchingAssignmentProblem problem, MatchingPlan plan) {
        Map<UUID, Integer> maxConcurrentOffersByOrderId = problem.orders().stream()
                .collect(Collectors.toMap(MatchingOrderInput::orderId, MatchingOrderInput::maxConcurrentOffers));

        Map<UUID, Long> proposalCountByOrderId = plan.proposals().stream()
                .collect(Collectors.groupingBy(MatchingProposal::orderId, Collectors.counting()));

        for (Map.Entry<UUID, Long> entry : proposalCountByOrderId.entrySet()) {
            UUID orderId = entry.getKey();
            Integer maxConcurrentOffers = maxConcurrentOffersByOrderId.get(orderId);
            if (maxConcurrentOffers == null) {
                throw new IllegalArgumentException("문제에 존재하지 않는 orderId에 대한 제안입니다: " + orderId);
            }
            long proposalCount = entry.getValue();
            if (proposalCount > maxConcurrentOffers) {
                throw new IllegalArgumentException(
                        "주문의 최대 동시 제안 수를 초과했습니다: orderId=" + orderId
                                + ", maxConcurrentOffers=" + maxConcurrentOffers + ", 실제 제안 수=" + proposalCount);
            }
        }

        Map<CandidateKey, MatchingCandidate> candidatesByKey = problem.candidates().stream()
                .collect(Collectors.toMap(
                        candidate -> new CandidateKey(candidate.orderId(), candidate.dreamiId()), candidate -> candidate));

        for (MatchingProposal proposal : plan.proposals()) {
            MatchingCandidate candidate = candidatesByKey.get(new CandidateKey(proposal.orderId(), proposal.dreamiId()));
            if (candidate == null) {
                throw new IllegalArgumentException(
                        "문제에 존재하지 않는 후보에 대한 제안입니다: orderId=" + proposal.orderId()
                                + ", dreamiId=" + proposal.dreamiId());
            }
            if (!eligibilityPolicy.isEligible(candidate, problem.evaluatedAt())) {
                throw new IllegalArgumentException("제안할 수 없는 후보입니다.");
            }
        }
    }

    private record CandidateKey(UUID orderId, UUID dreamiId) {
    }
}
