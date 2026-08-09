package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import com.naengsam.quick.domain.matching.policy.scoring.DistanceOnlyScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.ScarcityAwareScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.ScarcityScoreWeights;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ScoreBasedGreedyAssignmentPolicy에 서로 다른 MatchingScorePolicy를 주입했을 때, 단순히 한 주문 내부의
 * 드리미 순서만 바뀌는 게 아니라 "경합 중인 같은 드리미가 어느 주문에 배정되는지" 자체가 정책의 의도대로 달라지는지
 * 확인한다. 모든 테스트는 동일한 {@link #sharedProblem()}을 사용하며, Spring Context 없이 순수 단위 테스트로
 * 실행된다.
 * <p>시나리오: orderA와 orderB(둘 다 maxConcurrentOffers=1)가 dreamiShared를 공통 후보로 두고 경합한다.
 * dreamiShared는 orderA에는 가깝고(distance=10) orderB에는 멀다(distance=1000). 반대로 orderB는 훨씬 오래
 * 기다렸고(30분 vs 1분), orderA는 대체 후보가 dreamiShared 하나뿐이라 희소하고 orderB는 대체 후보(filler)가
 * 둘이나 있어 희소하지 않다. 이 상충하는 신호들 덕분에, 어떤 정책을 주입하느냐에 따라 dreamiShared의 배정처가
 * 뒤바뀐다.
 */
class AssignmentScoringPolicySubstitutionTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    private final UUID orderA = UUID.randomUUID();
    private final UUID orderB = UUID.randomUUID();
    private final UUID dreamiShared = UUID.randomUUID();
    private final UUID dreamiFiller1 = UUID.randomUUID();
    private final UUID dreamiFiller2 = UUID.randomUUID();

    @Test
    void 거리_우선_정책은_드리미를_더_가까운_주문에_배정한다() {
        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(new DistanceOnlyScorePolicy())
                .createPlan(sharedProblem());

        assertThat(orderOf(plan, dreamiShared)).isEqualTo(orderA);
        assertConstraintsHold(plan);
    }

    @Test
    void 주문_대기_우선_정책은_드리미를_더_오래_기다린_주문에_배정한다() {
        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(new OrderWaitScorePolicy())
                .createPlan(sharedProblem());

        assertThat(orderOf(plan, dreamiShared)).isEqualTo(orderB);
        assertConstraintsHold(plan);
    }

    @Test
    void 균형_정책은_거리_가중치를_높이면_거리_우선과_같은_결과를_낸다() {
        BalancedScorePolicy distanceHeavyPolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(90, 5, 5), 1000L, Duration.ofMinutes(30), Duration.ofMinutes(10));

        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(distanceHeavyPolicy).createPlan(sharedProblem());

        assertThat(orderOf(plan, dreamiShared)).isEqualTo(orderA);
        assertConstraintsHold(plan);
    }

    @Test
    void 균형_정책은_대기시간_가중치를_높이면_결과가_뒤바뀐다() {
        BalancedScorePolicy waitHeavyPolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(5, 90, 5), 1000L, Duration.ofMinutes(30), Duration.ofMinutes(10));

        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(waitHeavyPolicy).createPlan(sharedProblem());

        assertThat(orderOf(plan, dreamiShared)).isEqualTo(orderB);
        assertConstraintsHold(plan);
    }

    @Test
    void 희소성_정책은_드리미를_대체_후보가_적은_주문에_배정한다() {
        ScarcityAwareScorePolicy scarcityPolicy = new ScarcityAwareScorePolicy(
                candidate -> 0L, new ScarcityScoreWeights(0, 100, 0));

        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(scarcityPolicy).createPlan(sharedProblem());

        // orderA는 대체 후보가 dreamiShared 하나뿐(orderCandidateCount=1)이라 희소하고,
        // orderB는 filler가 둘 더 있어(orderCandidateCount=3) 희소하지 않다.
        assertThat(orderOf(plan, dreamiShared)).isEqualTo(orderA);
        assertConstraintsHold(plan);
    }

    /**
     * 모든 정책에서 동일하게 지켜져야 하는 배정 제약: 주문별 maxConcurrentOffers 준수, 드리미 전역 유일성.
     */
    private void assertConstraintsHold(MatchingPlan plan) {
        new MatchingPlanValidator(new LegacyOfferPolicy()).validate(sharedProblem(), plan);

        long dreamiShareCount = plan.proposals().size();
        long distinctDreamiCount = plan.proposals().stream().map(MatchingProposal::dreamiId).distinct().count();
        assertThat(distinctDreamiCount).isEqualTo(dreamiShareCount);
    }

    private UUID orderOf(MatchingPlan plan, UUID dreamiId) {
        return plan.proposals().stream()
                .filter(proposal -> proposal.dreamiId().equals(dreamiId))
                .map(MatchingProposal::orderId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("dreamiId에 대한 제안이 없습니다: " + dreamiId));
    }

    private MatchingAssignmentProblem sharedProblem() {
        return new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 1), orderInput(orderB, 1)),
                List.of(dreamiInput(dreamiShared), dreamiInput(dreamiFiller1), dreamiInput(dreamiFiller2)),
                List.of(
                        // orderA: 가깝고(10) 대기 짧고(1분), 대체 후보 없음(orderCandidateCount=1)
                        candidate(orderA, dreamiShared, 10L, Duration.ofMinutes(1), Duration.ofMinutes(5), 1, 2),
                        // orderB: 멀고(1000) 대기 김(30분), 대체 후보 있음(orderCandidateCount=3)
                        candidate(orderB, dreamiShared, 1000L, Duration.ofMinutes(30), Duration.ofMinutes(5), 3, 2),
                        candidate(orderB, dreamiFiller1, 1000L, Duration.ofMinutes(30), Duration.ofMinutes(1), 3, 1),
                        candidate(orderB, dreamiFiller2, 1000L, Duration.ofMinutes(30), Duration.ofMinutes(1), 3, 1)));
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate candidate(UUID orderId, UUID dreamiId, long distanceMeters,
            Duration orderWaitingTime, Duration dreamiWaitingTime, int orderCandidateCount, int dreamiCandidateCount) {
        return new MatchingCandidate(orderId, dreamiId, distanceMeters, orderWaitingTime, dreamiWaitingTime,
                orderCandidateCount, dreamiCandidateCount, Optional.empty());
    }
}
