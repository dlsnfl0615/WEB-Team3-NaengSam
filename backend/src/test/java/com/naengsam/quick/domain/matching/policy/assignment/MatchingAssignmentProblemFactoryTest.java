package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.DistanceOnlyScorePolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingAssignmentProblemFactory가 적격성 정책으로 원시 후보를 걸러 problem을 만들고, evaluatedAt을
 * eligibilityPolicy.isEligible(candidate, problem.evaluatedAt())에 그대로 전달하며, 걸러진 problem이
 * 배정 정책(Scoring → Assignment)에 그대로 이어지는지 확인한다.
 */
class MatchingAssignmentProblemFactoryTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    private final MatchingAssignmentProblemFactory factory =
            new MatchingAssignmentProblemFactory(new LegacyOfferPolicy());

    @Test
    void 부적격_후보는_제외되고_적격_후보만_남는다() {
        UUID orderId = UUID.randomUUID();
        UUID rejectedDreamiId = UUID.randomUUID();
        UUID eligibleDreamiId = UUID.randomUUID();
        List<MatchingCandidate> rawCandidates = List.of(
                candidateWithInteraction(orderId, rejectedDreamiId, PreviousOfferOutcome.DREAMI_REJECTED),
                candidateWithoutInteraction(orderId, eligibleDreamiId)
        );

        MatchingAssignmentProblem problem = factory.create(
                EVALUATED_AT,
                List.of(orderInput(orderId)),
                List.of(dreamiInput(rejectedDreamiId), dreamiInput(eligibleDreamiId)),
                rawCandidates
        );

        assertThat(problem.candidates()).hasSize(1);
        assertThat(problem.candidates().get(0).dreamiId()).isEqualTo(eligibleDreamiId);
    }

    @Test
    void 이전_이력이_없는_후보는_그대로_유지된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchingCandidate> rawCandidates = List.of(candidateWithoutInteraction(orderId, dreamiId));

        MatchingAssignmentProblem problem = factory.create(
                EVALUATED_AT, List.of(orderInput(orderId)), List.of(dreamiInput(dreamiId)), rawCandidates);

        assertThat(problem.candidates()).hasSize(1);
    }

    @Test
    void 생성된_problem은_전달받은_evaluatedAt을_그대로_갖는다() {
        MatchingAssignmentProblem problem = factory.create(EVALUATED_AT, List.of(), List.of(), List.of());

        assertThat(problem.evaluatedAt()).isEqualTo(EVALUATED_AT);
    }

    @Test
    void 필터링된_problem은_이어서_scoring과_assignment에_그대로_사용된다() {
        UUID orderId = UUID.randomUUID();
        UUID rejectedDreamiId = UUID.randomUUID();
        UUID eligibleDreamiId = UUID.randomUUID();
        List<MatchingCandidate> rawCandidates = List.of(
                candidateWithInteraction(orderId, rejectedDreamiId, PreviousOfferOutcome.DREAMI_REJECTED),
                candidateWithoutInteraction(orderId, eligibleDreamiId)
        );
        MatchingAssignmentProblem problem = factory.create(
                EVALUATED_AT,
                List.of(orderInput(orderId)),
                List.of(dreamiInput(rejectedDreamiId), dreamiInput(eligibleDreamiId)),
                rawCandidates
        );

        MatchingPlan plan = new ScoreBasedGreedyAssignmentPolicy(new DistanceOnlyScorePolicy()).createPlan(problem);

        assertThat(plan.proposals()).hasSize(1);
        assertThat(plan.proposals().get(0).dreamiId()).isEqualTo(eligibleDreamiId);
    }

    private MatchingOrderInput orderInput(UUID orderId) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, 1);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate candidateWithoutInteraction(UUID orderId, UUID dreamiId) {
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0, Optional.empty());
    }

    private MatchingCandidate candidateWithInteraction(UUID orderId, UUID dreamiId, PreviousOfferOutcome outcome) {
        PreviousOfferInteraction interaction = new PreviousOfferInteraction(outcome, EVALUATED_AT.minusMinutes(1));
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0, Optional.of(interaction));
    }
}
