package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 원시 후보 목록에 적격성 정책을 적용해 {@link MatchingAssignmentProblem}을 만든다.
 * <p>{@link MatchingAssignmentPolicy}는 problem.candidates()가 이미 필터링된 허용 목록이라고 전제하므로,
 * 거절·만료 이력을 읽어 후보를 제외하는 책임은 배정 정책이 아니라 이 팩토리에 있다. Eligibility(이 팩토리) →
 * Scoring → Assignment(배정 정책) 순서로 처리된다.
 * <p>orderCandidateCount/dreamiCandidateCount는 희소성 점수 계산에 쓰이므로 적격 후보만 남은 뒤 다시 세어야
 * 한다. 원시 후보 생성 시점 값은 부적격 후보를 포함하고 있어 신뢰할 수 없으므로, 필터링 후 이 팩토리가 값을
 * 덮어써 재계산한다.
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

        List<MatchingCandidate> recountedCandidates = recountCandidates(eligibleCandidates);

        return new MatchingAssignmentProblem(evaluatedAt, orders, dreamis, recountedCandidates);
    }

    private List<MatchingCandidate> recountCandidates(List<MatchingCandidate> eligibleCandidates) {
        Map<UUID, Long> orderCandidateCounts = eligibleCandidates.stream()
                .collect(Collectors.groupingBy(MatchingCandidate::orderId, Collectors.counting()));
        Map<UUID, Long> dreamiCandidateCounts = eligibleCandidates.stream()
                .collect(Collectors.groupingBy(MatchingCandidate::dreamiId, Collectors.counting()));

        return eligibleCandidates.stream()
                .map(candidate -> new MatchingCandidate(
                        candidate.orderId(),
                        candidate.dreamiId(),
                        candidate.distanceMeters(),
                        candidate.orderWaitingTime(),
                        candidate.dreamiWaitingTime(),
                        orderCandidateCounts.get(candidate.orderId()).intValue(),
                        dreamiCandidateCounts.get(candidate.dreamiId()).intValue(),
                        candidate.previousInteraction()
                ))
                .toList();
    }
}
