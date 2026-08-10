package com.naengsam.quick.domain.matching.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingCandidate 생성 시 필드 유효성 검증(이전 오퍼 이력 포함)이 올바르게 동작하는지, 그리고 값이 불변으로
 * 유지되는지 확인한다.
 */
class MatchingCandidateTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID DREAMI_ID = UUID.randomUUID();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

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
                ORDER_ID, DREAMI_ID, 100L, orderWaitingTime, dreamiWaitingTime, 3, 5, Optional.empty());

        assertThat(candidate.orderId()).isEqualTo(ORDER_ID);
        assertThat(candidate.dreamiId()).isEqualTo(DREAMI_ID);
        assertThat(candidate.distanceMeters()).isEqualTo(100L);
        assertThat(candidate.orderWaitingTime()).isEqualTo(orderWaitingTime);
        assertThat(candidate.dreamiWaitingTime()).isEqualTo(dreamiWaitingTime);
        assertThat(candidate.orderCandidateCount()).isEqualTo(3);
        assertThat(candidate.dreamiCandidateCount()).isEqualTo(5);
    }

    @Test
    void 이전_이력이_없는_Candidate를_생성할_수_있다() {
        MatchingCandidate candidate = candidateWithPreviousInteraction(Optional.empty());

        assertThat(candidate.previousInteraction()).isEmpty();
    }

    @Test
    void 이전_이력이_있는_Candidate를_생성할_수_있다() {
        PreviousOfferInteraction interaction =
                new PreviousOfferInteraction(PreviousOfferOutcome.DREAMI_REJECTED, OCCURRED_AT);

        MatchingCandidate candidate = candidateWithPreviousInteraction(Optional.of(interaction));

        assertThat(candidate.previousInteraction()).contains(interaction);
    }

    @Test
    void previousInteraction이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingCandidate(
                ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, Duration.ZERO, 0, 0, null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void PreviousOfferInteraction의_outcome이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new PreviousOfferInteraction(null, OCCURRED_AT));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void PreviousOfferInteraction의_occurredAt이_null이면_예외가_발생한다() {
        Throwable thrown =
                catchThrowable(() -> new PreviousOfferInteraction(PreviousOfferOutcome.DREAMI_REJECTED, null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_필드로_생성한_Candidate는_값이_같다() {
        PreviousOfferInteraction interaction =
                new PreviousOfferInteraction(PreviousOfferOutcome.WITHDRAWN, OCCURRED_AT);

        MatchingCandidate first = candidateWithPreviousInteraction(Optional.of(interaction));
        MatchingCandidate second = candidateWithPreviousInteraction(Optional.of(interaction));

        assertThat(first).isEqualTo(second);
    }

    private MatchingCandidate candidateWithPreviousInteraction(Optional<PreviousOfferInteraction> previousInteraction) {
        return new MatchingCandidate(
                ORDER_ID, DREAMI_ID, 0L, Duration.ZERO, Duration.ZERO, 0, 0, previousInteraction);
    }

    private Throwable catchCandidateCreation(UUID orderId, UUID dreamiId, long distanceMeters,
            Duration orderWaitingTime, Duration dreamiWaitingTime, int orderCandidateCount, int dreamiCandidateCount) {
        return catchThrowable(() -> new MatchingCandidate(
                orderId, dreamiId, distanceMeters, orderWaitingTime, dreamiWaitingTime,
                orderCandidateCount, dreamiCandidateCount, Optional.empty()));
    }
}
