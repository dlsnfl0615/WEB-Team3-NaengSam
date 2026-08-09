package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingAssignmentProblem이 불변 컬렉션을 보장하고, 중복 ID를 거부하며, 빈 배치를 허용하는지 확인한다.
 */
class MatchingAssignmentProblemTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void orders와_dreamis가_비어있어도_생성된다() {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(List.of(), List.of());

        assertThat(problem.orders()).isEmpty();
        assertThat(problem.dreamis()).isEmpty();
    }

    @Test
    void orders_목록은_불변이다() {
        List<MatchingOrderInput> mutableOrders = new ArrayList<>();
        mutableOrders.add(orderInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(mutableOrders, List.of());

        Throwable thrown = catchThrowable(() -> problem.orders().add(orderInput(UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dreamis_목록은_불변이다() {
        List<MatchingDreamiInput> mutableDreamis = new ArrayList<>();
        mutableDreamis.add(dreamiInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(List.of(), mutableDreamis);

        Throwable thrown = catchThrowable(() -> problem.dreamis().add(dreamiInput(UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 원본_목록을_수정해도_문제_내부_목록은_바뀌지_않는다() {
        List<MatchingOrderInput> mutableOrders = new ArrayList<>();
        mutableOrders.add(orderInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(mutableOrders, List.of());

        mutableOrders.add(orderInput(UUID.randomUUID()));

        assertThat(problem.orders()).hasSize(1);
    }

    @Test
    void orderId가_중복되면_예외가_발생한다() {
        UUID duplicatedOrderId = UUID.randomUUID();
        List<MatchingOrderInput> orders =
                List.of(orderInput(duplicatedOrderId), orderInput(duplicatedOrderId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(orders, List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiId가_중복되면_예외가_발생한다() {
        UUID duplicatedDreamiId = UUID.randomUUID();
        List<MatchingDreamiInput> dreamis =
                List.of(dreamiInput(duplicatedDreamiId), dreamiInput(duplicatedDreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), dreamis));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orders가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(null, List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamis가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingOrderInput orderInput(UUID orderId) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, 1);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }
}
