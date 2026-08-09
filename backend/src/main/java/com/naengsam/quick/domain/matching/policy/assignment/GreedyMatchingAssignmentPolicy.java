package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 각 주문을 {@link MatchingAssignmentProblem#orders()}의 앞에서부터 순회하며, 아직 다른 주문에 배정되지 않은
 * 후보 중 점수가 낮은(우선순위가 높은) 순으로 주문의 maxConcurrentOffers만큼 뽑아 제안을 만드는 탐욕(greedy)
 * 배정 정책.
 * <p>정렬 기준은 (1) {@link MatchingScorePolicy#score(MatchingCandidate)} 오름차순, (2) 동점이면
 * dreamiWaitingTime 내림차순(더 오래 기다린 드리미 우선), (3) 그래도 동점이면 dreamiId 오름차순으로 완전히
 * 결정된다.
 * <p>{@link com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy}와 함께 쓰면, 같은
 * 주문에 속한 모든 후보의 orderWaitingTime이 동일해 점수가 항상 동점이 되므로 실질적인 순서는 (2)번 기준,
 * 즉 dreamiWaitingTime 내림차순(레거시 WaitingDreami.updatedAt 오름차순과 동치, FIFO)으로 결정된다. 이는
 * legacy MatchingService.attemptOfferRound가 대기 드리미를 updatedAt 오름차순으로 정렬해 최대
 * MAX_OFFER_COUNT(여기서는 주문별 maxConcurrentOffers)만큼 뽑던 동작을 그대로 재현한다.
 * <p>problem.candidates()는 이미 필터링된 허용 목록이므로, 이 정책은 거절/만료 이력을 직접 조회하지 않고
 * candidates에 존재하는 조합만 사용한다. 같은 드리미가 여러 주문의 후보로 나타나더라도, orders 입력 목록의
 * 앞에 있는 주문이 그 드리미를 소비하면 이후 주문에서는 후보에서 제외된다(배치 내 드리미 전역 유일성).
 */
public class GreedyMatchingAssignmentPolicy implements MatchingAssignmentPolicy {

    private final MatchingScorePolicy scorePolicy;

    public GreedyMatchingAssignmentPolicy(MatchingScorePolicy scorePolicy) {
        this.scorePolicy = scorePolicy;
    }

    @Override
    public MatchingPlan createPlan(MatchingAssignmentProblem problem) {
        Map<UUID, List<MatchingCandidate>> candidatesByOrderId = problem.candidates().stream()
                .collect(Collectors.groupingBy(MatchingCandidate::orderId));

        Comparator<MatchingCandidate> comparator = Comparator
                .comparingLong(scorePolicy::score)
                .thenComparing(Comparator.comparing(MatchingCandidate::dreamiWaitingTime).reversed())
                .thenComparing(MatchingCandidate::dreamiId);

        Set<UUID> consumedDreamiIds = new HashSet<>();
        List<MatchingProposal> proposals = new ArrayList<>();

        for (MatchingOrderInput order : problem.orders()) {
            List<MatchingCandidate> orderCandidates = candidatesByOrderId.getOrDefault(order.orderId(), List.of());

            List<MatchingCandidate> selected = orderCandidates.stream()
                    .filter(candidate -> !consumedDreamiIds.contains(candidate.dreamiId()))
                    .sorted(comparator)
                    .limit(order.maxConcurrentOffers())
                    .toList();

            for (MatchingCandidate candidate : selected) {
                proposals.add(new MatchingProposal(order.orderId(), candidate.dreamiId()));
                consumedDreamiIds.add(candidate.dreamiId());
            }
        }

        return new MatchingPlan(proposals);
    }
}
