package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingPlanValidator가 MatchingAssignmentProblem의 주문별 maxConcurrentOffers 제약을 넘는 계획을
 * 거부하는지 확인한다.
 */
class MatchingPlanValidatorTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    private final MatchingPlanValidator validator = new MatchingPlanValidator();

    @Test
    void 제안_수가_maxConcurrentOffers_이내면_통과한다() {
        UUID orderId = UUID.randomUUID();
        MatchingAssignmentProblem problem =
                new MatchingAssignmentProblem(List.of(orderInput(orderId, 3)), List.of());
        MatchingPlan plan = new MatchingPlan(Arrays.asList(
                new MatchingProposal(orderId, UUID.randomUUID()),
                new MatchingProposal(orderId, UUID.randomUUID())));

        assertThat(catchThrowable(() -> validator.validate(problem, plan))).isNull();
    }

    @Test
    void 제안_수가_maxConcurrentOffers를_초과하면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        MatchingAssignmentProblem problem =
                new MatchingAssignmentProblem(List.of(orderInput(orderId, 1)), List.of());
        MatchingPlan plan = new MatchingPlan(Arrays.asList(
                new MatchingProposal(orderId, UUID.randomUUID()),
                new MatchingProposal(orderId, UUID.randomUUID())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문제에_없는_orderId에_대한_제안이면_예외가_발생한다() {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(List.of(), List.of());
        MatchingPlan plan = new MatchingPlan(List.of(new MatchingProposal(UUID.randomUUID(), UUID.randomUUID())));

        Throwable thrown = catchThrowable(() -> validator.validate(problem, plan));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }
}
