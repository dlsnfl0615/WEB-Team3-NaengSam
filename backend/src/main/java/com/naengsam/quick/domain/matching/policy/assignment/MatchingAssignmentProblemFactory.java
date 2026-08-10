package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 원시 후보 목록에 적격성 정책을 적용해 {@link MatchingAssignmentProblem}을 만든다.
 * <p>{@link MatchingAssignmentPolicy}는 problem.candidates()가 이미 필터링된 허용 목록이라고 전제하므로,
 * 거절·만료 이력을 읽어 후보를 제외하는 책임은 배정 정책이 아니라 이 팩토리에 있다. Eligibility(이 팩토리) →
 * Scoring → Assignment(배정 정책) 순서로 처리된다.
 */
public class MatchingAssignmentProblemFactory {

    private final MatchingEligibilityPolicy eligibilityPolicy;

    public MatchingAssignmentProblemFactory(MatchingEligibilityPolicy eligibilityPolicy) {
        this.eligibilityPolicy = eligibilityPolicy;
    }

    public MatchingAssignmentProblem create(
            LocalDateTime evaluatedAt,
            List<MatchingOrderInput> orders,
            List<MatchingDreamiInput> dreamis,
            List<MatchingCandidate> rawCandidates
    ) {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(evaluatedAt, orders, dreamis, rawCandidates);

        List<MatchingCandidate> eligibleCandidates = problem.candidates().stream()
                .filter(candidate -> eligibilityPolicy.isEligible(candidate, problem.evaluatedAt()))
                .toList();

        return new MatchingAssignmentProblem(evaluatedAt, orders, dreamis, eligibleCandidates);
    }
}
