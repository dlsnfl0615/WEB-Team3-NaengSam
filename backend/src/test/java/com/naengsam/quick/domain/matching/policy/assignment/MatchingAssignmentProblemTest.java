package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingAssignmentProblem이 불변 컬렉션을 보장하고, 중복 ID·중복 후보를 거부하며, 빈 배치를 허용하고, 후보의
 * orderId·dreamiId가 orders·dreamis에 실제로 존재하는지 검증하는지 확인한다.
 */
class MatchingAssignmentProblemTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void orders_dreamis_candidates가_모두_비어있어도_생성된다() {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(List.of(), List.of(), List.of());

        assertThat(problem.orders()).isEmpty();
        assertThat(problem.dreamis()).isEmpty();
        assertThat(problem.candidates()).isEmpty();
    }

    @Test
    void orders_목록은_불변이다() {
        List<MatchingOrderInput> mutableOrders = new ArrayList<>();
        mutableOrders.add(orderInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(mutableOrders, List.of(), List.of());

        Throwable thrown = catchThrowable(() -> problem.orders().add(orderInput(UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dreamis_목록은_불변이다() {
        List<MatchingDreamiInput> mutableDreamis = new ArrayList<>();
        mutableDreamis.add(dreamiInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(List.of(), mutableDreamis, List.of());

        Throwable thrown = catchThrowable(() -> problem.dreamis().add(dreamiInput(UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void candidates_목록은_불변이다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchingCandidate> mutableCandidates = new ArrayList<>();
        mutableCandidates.add(candidate(orderId, dreamiId));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId)), List.of(dreamiInput(dreamiId)), mutableCandidates);

        Throwable thrown = catchThrowable(() -> problem.candidates().add(candidate(orderId, dreamiId)));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 원본_목록을_수정해도_문제_내부_목록은_바뀌지_않는다() {
        List<MatchingOrderInput> mutableOrders = new ArrayList<>();
        mutableOrders.add(orderInput(UUID.randomUUID()));
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(mutableOrders, List.of(), List.of());

        mutableOrders.add(orderInput(UUID.randomUUID()));

        assertThat(problem.orders()).hasSize(1);
    }

    @Test
    void orderId가_중복되면_예외가_발생한다() {
        UUID duplicatedOrderId = UUID.randomUUID();
        List<MatchingOrderInput> orders =
                List.of(orderInput(duplicatedOrderId), orderInput(duplicatedOrderId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(orders, List.of(), List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiId가_중복되면_예외가_발생한다() {
        UUID duplicatedDreamiId = UUID.randomUUID();
        List<MatchingDreamiInput> dreamis =
                List.of(dreamiInput(duplicatedDreamiId), dreamiInput(duplicatedDreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), dreamis, List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orders가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(null, List.of(), List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamis가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), null, List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void candidates가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), List.of(), null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void candidate에_null_원소가_있으면_예외가_발생한다() {
        List<MatchingCandidate> candidates = new ArrayList<>();
        candidates.add(null);

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(List.of(), List.of(), candidates));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void candidate의_orderId가_orders에_없으면_예외가_발생한다() {
        UUID dreamiId = UUID.randomUUID();
        List<MatchingCandidate> candidates = List.of(candidate(UUID.randomUUID(), dreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(
                List.of(), List.of(dreamiInput(dreamiId)), candidates));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void candidate의_dreamiId가_dreamis에_없으면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        List<MatchingCandidate> candidates = List.of(candidate(orderId, UUID.randomUUID()));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(
                List.of(orderInput(orderId)), List.of(), candidates));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_orderId와_dreamiId_후보가_중복되면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchingCandidate> candidates = List.of(candidate(orderId, dreamiId), candidate(orderId, dreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingAssignmentProblem(
                List.of(orderInput(orderId)), List.of(dreamiInput(dreamiId)), candidates));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orders_dreamis에_있는_후보는_정상_생성된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchingCandidate> candidates = List.of(candidate(orderId, dreamiId));

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(
                List.of(orderInput(orderId)), List.of(dreamiInput(dreamiId)), candidates);

        assertThat(problem.candidates()).hasSize(1);
    }

    private MatchingOrderInput orderInput(UUID orderId) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, 1);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate candidate(UUID orderId, UUID dreamiId) {
        return new MatchingCandidate(orderId, dreamiId, 0L, Duration.ZERO, Duration.ZERO, 0, 0);
    }
}
