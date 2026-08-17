package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
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
 * 후보 중 점수가 낮은(우선순위가 높은) 순으로 주문의 maxConcurrentOffers만큼 뽑아 제안을 만드는 정책.
 * <p><b>orders 목록에서 앞선 주문이 항상 먼저 후보를 골라간다.</b> 즉 어떤 scorePolicy를 주입하든 주문 간
 * 우선순위는 scorePolicy가 아니라 orders 입력 순서가 결정한다. 이는 결함이 아니라 legacy
 * {@code MatchingService.attemptOfferRound}(이벤트 하나당 주문 하나씩 처리, 배치 개념 자체가 없음)를 그대로
 * 재현하기 위한 의도적 설계다. {@link com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy}
 * 와 함께 쓰면, 같은 주문에 속한 모든 후보의 orderWaitingTime이 동일해 점수가 항상 동점이 되므로 실질적인 순서는
 * dreamiWaitingTime 내림차순(레거시 WaitingDreami.updatedAt 오름차순과 동치, FIFO)으로 결정되어 legacy의
 * 선택 결과를 재현한다.
 * <p>거리·균형·희소성처럼 <b>주문 간 우선순위 자체를 점수로 매기고 싶다면 이 클래스가 아니라
 * {@link ScoreBasedGreedyAssignmentPolicy}를 사용해야 한다.</b> 이 클래스에 다른 scorePolicy를 주입해도
 * orders 앞쪽 주문이 항상 먼저 후보를 골라가는 구조는 그대로라, 예를 들어 거리 정책을 넣어도 입력 순서상 뒤에 있는
 * 주문은 아무리 거리가 가까운 후보를 갖고 있어도 앞 주문에 후보를 빼앗길 수 있다.
 * <p>problem.candidates()는 이미 필터링된 허용 목록이므로, 이 정책은 거절/만료 이력을 직접 조회하지 않고
 * candidates에 존재하는 조합만 사용한다.
 * <p>제안이 확정되는 순간 {@link com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot#capture}로
 * 그 후보에 적용 중인 offer scope를 스냅샷으로 남겨 {@link MatchingProposal}에 싣는다.
 */
public class LegacyOrderFirstAssignmentPolicy implements MatchingAssignmentPolicy {

    private final MatchingScorePolicy scorePolicy;
    private final OfferScopeResolver offerScopeResolver;

    public LegacyOrderFirstAssignmentPolicy(MatchingScorePolicy scorePolicy, OfferScopeResolver offerScopeResolver) {
        this.scorePolicy = scorePolicy;
        this.offerScopeResolver = offerScopeResolver;
    }

    @Override
    public MatchingPlan createPlan(MatchingAssignmentProblem problem) {
        Map<UUID, List<MatchingCandidate>> candidatesByOrderId = problem.candidates().stream()
                .collect(Collectors.groupingBy(MatchingCandidate::orderId));

        Comparator<MatchingCandidate> comparator = Comparator
                .<MatchingCandidate>comparingLong(scorePolicy::score)
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
                OfferPolicySnapshot snapshot =
                        OfferPolicySnapshot.capture(offerScopeResolver, candidate, problem.evaluatedAt());
                proposals.add(new MatchingProposal(order.orderId(), candidate.dreamiId(), snapshot));
                consumedDreamiIds.add(candidate.dreamiId());
            }
        }

        return new MatchingPlan(proposals);
    }
}
