package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 간 우선순위까지 scorePolicy가 직접 결정하도록, 모든 주문-드리미 후보 쌍을 하나의 전역 순서로 정렬한 뒤
 * 앞에서부터 순서대로 배정하는 탐욕(greedy) 배정 정책.
 * <p>정렬 기준은 (1) {@link MatchingScorePolicy#score(MatchingCandidate)} 오름차순, (2) 동점이면
 * orderWaitingTime 내림차순(더 오래 기다린 주문 우선), (3) 그래도 동점이면 dreamiWaitingTime 내림차순(더 오래
 * 기다린 드리미 우선), (4) 그래도 동점이면 orderId 오름차순, (5) 그래도 동점이면 dreamiId 오름차순으로 완전히
 * 결정된다.
 * <p>이렇게 정렬된 후보를 앞에서부터 순회하며, 그 드리미가 아직 배정되지 않았고 그 주문이 아직
 * maxConcurrentOffers에 도달하지 않았으면 배정하고, 아니면 건너뛴다.
 * <p>{@link LegacyOrderFirstAssignmentPolicy}와 달리 주문을 orders 입력 순서대로 순회하지 않는다 — 입력
 * 순서는 결정적이지 않고, orders 목록에서 앞선 주문이 항상 먼저 후보를 골라가 버리면 scorePolicy를 거리·균형·
 * 희소성 등으로 교체해도 그 효과가 주문 우선순위에는 반영되지 않는 문제가 있었다. 이 클래스는 모든 후보를 하나의
 * 전역 순서로 매겨 그 문제를 없앤다. legacy 재현이 필요하면 이 클래스가 아니라
 * {@link LegacyOrderFirstAssignmentPolicy}를 사용해야 한다.
 * <p>problem.candidates()는 이미 필터링된 허용 목록이므로, 이 정책은 거절/만료 이력을 직접 조회하지 않고
 * candidates에 존재하는 조합만 사용한다.
 */
public class ScoreBasedGreedyAssignmentPolicy implements MatchingAssignmentPolicy {

    private final MatchingScorePolicy scorePolicy;

    public ScoreBasedGreedyAssignmentPolicy(MatchingScorePolicy scorePolicy) {
        this.scorePolicy = scorePolicy;
    }

    @Override
    public MatchingPlan createPlan(MatchingAssignmentProblem problem) {
        Map<UUID, Integer> maxConcurrentOffersByOrderId = problem.orders().stream()
                .collect(Collectors.toMap(MatchingOrderInput::orderId, MatchingOrderInput::maxConcurrentOffers));

        Comparator<MatchingCandidate> comparator = Comparator
                .comparingLong(scorePolicy::score)
                .thenComparing(Comparator.comparing(MatchingCandidate::orderWaitingTime).reversed())
                .thenComparing(Comparator.comparing(MatchingCandidate::dreamiWaitingTime).reversed())
                .thenComparing(MatchingCandidate::orderId)
                .thenComparing(MatchingCandidate::dreamiId);

        List<MatchingCandidate> sortedCandidates = problem.candidates().stream().sorted(comparator).toList();

        Set<UUID> consumedDreamiIds = new HashSet<>();
        Map<UUID, Integer> assignedCountByOrderId = new HashMap<>();
        List<MatchingProposal> proposals = new ArrayList<>();

        for (MatchingCandidate candidate : sortedCandidates) {
            if (consumedDreamiIds.contains(candidate.dreamiId())) {
                continue;
            }
            int maxConcurrentOffers = maxConcurrentOffersByOrderId.get(candidate.orderId());
            int assignedCount = assignedCountByOrderId.getOrDefault(candidate.orderId(), 0);
            if (assignedCount >= maxConcurrentOffers) {
                continue;
            }

            proposals.add(new MatchingProposal(candidate.orderId(), candidate.dreamiId()));
            consumedDreamiIds.add(candidate.dreamiId());
            assignedCountByOrderId.put(candidate.orderId(), assignedCount + 1);
        }

        return new MatchingPlan(proposals);
    }
}
