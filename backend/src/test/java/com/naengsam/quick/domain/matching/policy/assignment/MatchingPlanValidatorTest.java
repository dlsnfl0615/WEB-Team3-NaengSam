package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingPlanValidator가 MatchingAssignmentProblem의 주문별 maxConcurrentOffers 제약을 넘는 계획을
 * 거부하고, 문제에 없는 후보나 적격성 정책상 부적격한 후보에 대한 제안을 거부하는지 확인한다.
 */
class MatchingPlanValidatorTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    private final MatchingPlanValidator validator = new MatchingPlanValidator(new LegacyOfferPolicy());

    @Test
    void 제안_수가_maxConcurrentOffers_이내면_통과한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 3)), List.of(dreamiInput(dreami1), dreamiInput(dreami2)),
                List.of(eligibleCandidate(orderId, dreami1), eligibleCandidate(orderId, dreami2)));
        MatchingPlan plan = new MatchingPlan(Arrays.asList(
                new MatchingProposal(orderId, dreami1, snapshot()),
                new MatchingProposal(orderId, dreami2, snapshot())));

        assertThat(catchThrowable(() -> validator.validate(problem, plan))).isNull();
    }

    @Test
    void 제안_수가_maxConcurrentOffers를_초과하면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        MatchingAssignmentProblem problem =
                new MatchingAssignmentProblem(EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(), List.of());
        MatchingPlan plan = new MatchingPlan(Arrays.asList(
                new MatchingProposal(orderId, UUID.randomUUID(), snapshot()),
                new MatchingProposal(orderId, UUID.randomUUID(), snapshot())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문제에_없는_orderId에_대한_제안이면_예외가_발생한다() {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT, List.of(), List.of(), List.of());
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(UUID.randomUUID(), UUID.randomUUID(), snapshot())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문제에_없는_후보에_대한_제안이면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(), List.of());
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 적격성_정책상_제외된_후보에_대한_제안이면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(dreamiInput(dreamiId)),
                List.of(rejectedCandidate(orderId, dreamiId)));
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, snapshot())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 제안의_offer_scope_스냅샷이_실제_후보_거리와_다르면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                EVALUATED_AT, List.of(orderInput(orderId, 1)), List.of(dreamiInput(dreamiId)),
                List.of(eligibleCandidate(orderId, dreamiId))); // candidate의 distanceMeters는 0
        OfferPolicySnapshot staleSnapshot = new OfferPolicySnapshot(Duration.ZERO, EVALUATED_AT, 0L, 500.0, 3_000);
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(orderId, dreamiId, staleSnapshot)));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private OfferPolicySnapshot snapshot() {
        return new OfferPolicySnapshot(Duration.ZERO, EVALUATED_AT, 0L, 0.0, 3_000);
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate eligibleCandidate(UUID orderId, UUID dreamiId) {
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0, Optional.empty());
    }

    private MatchingCandidate rejectedCandidate(UUID orderId, UUID dreamiId) {
        PreviousOfferInteraction interaction =
                new PreviousOfferInteraction(PreviousOfferOutcome.DREAMI_REJECTED, EVALUATED_AT.minusMinutes(1));
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0, Optional.of(interaction));
    }
}
