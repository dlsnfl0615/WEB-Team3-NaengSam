package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * LegacyOrderFirstAssignmentPolicy가 legacy MatchingService.attemptOfferRound의 선택 로직(대기 오래한
 * 드리미 우선, 주문별 상한까지 선택)을 그대로 재현하는지, 그리고 배정 계약(후보 목록 준수, 드리미 전역 유일성,
 * 결정성)을 지키는지 확인한다.
 */
class LegacyOrderFirstAssignmentPolicyTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    private final LegacyOrderFirstAssignmentPolicy policy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());

    @Test
    void 후보가_maxConcurrentOffers보다_많으면_상한만큼만_제안한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();
        UUID dreami4 = UUID.randomUUID();
        UUID dreami5 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId, 3)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2), dreamiInput(dreami3),
                        dreamiInput(dreami4), dreamiInput(dreami5)),
                List.of(
                        candidate(orderId, dreami1, Duration.ofMinutes(1)),
                        candidate(orderId, dreami2, Duration.ofMinutes(5)),
                        candidate(orderId, dreami3, Duration.ofMinutes(4)),
                        candidate(orderId, dreami4, Duration.ofMinutes(3)),
                        candidate(orderId, dreami5, Duration.ofMinutes(2))));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).hasSize(3);
        assertThat(proposedDreamiIds(plan)).containsExactly(dreami2, dreami3, dreami4);
    }

    @Test
    void 후보가_maxConcurrentOffers보다_적으면_전부_제안한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId, 3)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2)),
                List.of(candidate(orderId, dreami1, Duration.ofMinutes(1)),
                        candidate(orderId, dreami2, Duration.ofMinutes(2))));

        Throwable thrown = catchThrowable(() -> {
            MatchingPlan plan = policy.createPlan(problem);
            assertThat(plan.proposals()).hasSize(2);
            assertThat(proposedDreamiIds(plan)).containsExactly(dreami2, dreami1);
        });

        assertThat(thrown).isNull();
    }

    @Test
    void 후보가_없는_주문은_제안이_생성되지_않는다() {
        UUID orderId = UUID.randomUUID();
        MatchingAssignmentProblem problem =
                new MatchingAssignmentProblem(List.of(orderInput(orderId, 3)), List.of(), List.of());

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).isEmpty();
    }

    @Test
    void candidates에_없는_드리미는_결과에_나오지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID includedDreami = UUID.randomUUID();
        UUID excludedDreami = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId, 3)),
                List.of(dreamiInput(includedDreami), dreamiInput(excludedDreami)),
                List.of(candidate(orderId, includedDreami, Duration.ofMinutes(1))));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(proposedDreamiIds(plan)).containsExactly(includedDreami);
    }

    @Test
    void 같은_드리미가_여러_주문의_후보이면_orders의_앞_주문이_먼저_선점한다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID sharedDreami = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderA, 1), orderInput(orderB, 1)),
                List.of(dreamiInput(sharedDreami)),
                List.of(
                        candidate(orderA, sharedDreami, Duration.ofMinutes(1)),
                        candidate(orderB, sharedDreami, Duration.ofMinutes(1))));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).containsExactly(new MatchingProposal(orderA, sharedDreami));
    }

    @Test
    void 같은_문제에_두_번_호출해도_같은_결과가_나온다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId, 2)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2), dreamiInput(dreami3)),
                List.of(
                        candidate(orderId, dreami1, Duration.ofMinutes(1)),
                        candidate(orderId, dreami2, Duration.ofMinutes(2)),
                        candidate(orderId, dreami3, Duration.ofMinutes(3))));

        MatchingPlan first = policy.createPlan(problem);
        MatchingPlan second = policy.createPlan(problem);

        assertThat(first).isEqualTo(second);
        assertThat(first.proposals()).containsExactlyElementsOf(second.proposals());
    }

    @Test
    void score가_다르면_dreamiWaitingTime보다_score가_우선한다() {
        UUID orderId = UUID.randomUUID();
        UUID betterScoreShortWait = UUID.randomUUID();
        UUID worseScoreLongWait = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId, 1)),
                List.of(dreamiInput(betterScoreShortWait), dreamiInput(worseScoreLongWait)),
                List.of(
                        candidateWithDistance(orderId, betterScoreShortWait, 10L, Duration.ofMinutes(1)),
                        candidateWithDistance(orderId, worseScoreLongWait, 100L, Duration.ofMinutes(30))));

        LegacyOrderFirstAssignmentPolicy distanceScoredPolicy =
                new LegacyOrderFirstAssignmentPolicy(MatchingCandidate::distanceMeters);

        MatchingPlan plan = distanceScoredPolicy.createPlan(problem);

        assertThat(proposedDreamiIds(plan)).containsExactly(betterScoreShortWait);
    }

    private List<UUID> proposedDreamiIds(MatchingPlan plan) {
        return plan.proposals().stream().map(MatchingProposal::dreamiId).collect(Collectors.toList());
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate candidate(UUID orderId, UUID dreamiId, Duration dreamiWaitingTime) {
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, dreamiWaitingTime, 0, 0);
    }

    private MatchingCandidate candidateWithDistance(
            UUID orderId, UUID dreamiId, long distanceMeters, Duration dreamiWaitingTime) {
        return new MatchingCandidate(orderId, dreamiId, distanceMeters, Duration.ZERO, dreamiWaitingTime, 0, 0);
    }
}
