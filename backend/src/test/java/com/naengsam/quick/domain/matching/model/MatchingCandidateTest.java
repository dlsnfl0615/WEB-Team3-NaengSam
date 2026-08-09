package com.naengsam.quick.domain.matching.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingCandidate 생성 시 필드 유효성 검증이 올바르게 동작하는지 확인한다.
 */
class MatchingCandidateTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID DREAMI_ID = UUID.randomUUID();

    @Test
    void orderId가_null이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(null, DREAMI_ID, 0L, Duration.ZERO, Duration.ZERO, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiId가_null이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, null, 0L, Duration.ZERO, Duration.ZERO, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanceMeters가_음수이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, -1L, Duration.ZERO, Duration.ZERO, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderWaitingTime이_null이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, null, Duration.ZERO, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderWaitingTime이_음수이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, Duration.ofSeconds(-1), Duration.ZERO, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiWaitingTime이_null이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, null, 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiWaitingTime이_음수이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, Duration.ofSeconds(-1), 0, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderCandidateCount가_음수이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, Duration.ZERO, -1, 0);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiCandidateCount가_음수이면_예외가_발생한다() {
        Throwable thrown = catchCandidateCreation(ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, Duration.ZERO, 0, -1);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정상_필드로_생성하면_값이_그대로_저장된다() {
        Duration orderWaitingTime = Duration.ofSeconds(30);
        Duration dreamiWaitingTime = Duration.ofSeconds(45);

        MatchingCandidate candidate = new MatchingCandidate(
                ORDER_ID, DREAMI_ID, 100L, orderWaitingTime, dreamiWaitingTime, 3, 5);

        assertThat(candidate.orderId()).isEqualTo(ORDER_ID);
        assertThat(candidate.dreamiId()).isEqualTo(DREAMI_ID);
        assertThat(candidate.distanceMeters()).isEqualTo(100L);
        assertThat(candidate.orderWaitingTime()).isEqualTo(orderWaitingTime);
        assertThat(candidate.dreamiWaitingTime()).isEqualTo(dreamiWaitingTime);
        assertThat(candidate.orderCandidateCount()).isEqualTo(3);
        assertThat(candidate.dreamiCandidateCount()).isEqualTo(5);
    }

    private Throwable catchCandidateCreation(UUID orderId, UUID dreamiId, long distanceMeters,
            Duration orderWaitingTime, Duration dreamiWaitingTime, int orderCandidateCount, int dreamiCandidateCount) {
        return catchThrowable(() -> new MatchingCandidate(
                orderId, dreamiId, distanceMeters, orderWaitingTime, dreamiWaitingTime,
                orderCandidateCount, dreamiCandidateCount));
    }
}
