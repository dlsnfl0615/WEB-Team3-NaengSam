package com.naengsam.quick.domain.matching.policy.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OutcomeCooldownOfferPolicy가 이전 오퍼 결과별 cooldown을 적용하고, 드리미 책임이 아닌 종료 결과는
 * 즉시 다시 허용하며, 이력 발생 시각이 평가 시각보다 미래인 잘못된 입력을 거부하는지 확인한다.
 */
class OutcomeCooldownOfferPolicyTest {

    private static final LocalDateTime REJECTED_AT = LocalDateTime.of(2026, 8, 9, 12, 0);
    private static final Duration DREAMI_REJECTION_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration BOORMI_REJECTION_COOLDOWN = Duration.ofMinutes(10);
    private static final Duration DREAMI_EXPIRATION_COOLDOWN = Duration.ofMinutes(3);

    private final OutcomeCooldownOfferPolicy policy = new OutcomeCooldownOfferPolicy(
            DREAMI_REJECTION_COOLDOWN,
            BOORMI_REJECTION_COOLDOWN,
            DREAMI_EXPIRATION_COOLDOWN
    );

    @Test
    void 이전_이력이_없으면_적격하다() {
        MatchingCandidate candidate = candidateWithPreviousInteraction(Optional.empty());

        assertThat(policy.isEligible(candidate, REJECTED_AT)).isTrue();
    }

    @Test
    void 드리미_거절_cooldown_전이면_제외한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.DREAMI_REJECTED, REJECTED_AT);
        LocalDateTime evaluatedAt = REJECTED_AT.plusMinutes(4).plusSeconds(59);

        assertThat(policy.isEligible(candidate, evaluatedAt)).isFalse();
    }

    @Test
    void 드리미_거절_cooldown이_정확히_지나면_허용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.DREAMI_REJECTED, REJECTED_AT);
        LocalDateTime evaluatedAt = REJECTED_AT.plusMinutes(5);

        assertThat(policy.isEligible(candidate, evaluatedAt)).isTrue();
    }

    @Test
    void 부르미_거절에는_별도_cooldown을_적용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.BOORMI_REJECTED, REJECTED_AT);

        assertThat(policy.isEligible(candidate, REJECTED_AT.plusMinutes(9).plusSeconds(59))).isFalse();
        assertThat(policy.isEligible(candidate, REJECTED_AT.plusMinutes(10))).isTrue();
    }

    @Test
    void 드리미_응답_timeout에는_별도_cooldown을_적용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.DREAMI_EXPIRED, REJECTED_AT);

        assertThat(policy.isEligible(candidate, REJECTED_AT.plusMinutes(2).plusSeconds(59))).isFalse();
        assertThat(policy.isEligible(candidate, REJECTED_AT.plusMinutes(3))).isTrue();
    }

    @Test
    void 부르미_응답_timeout은_즉시_허용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.BOORMI_EXPIRED, REJECTED_AT);

        assertThat(policy.isEligible(candidate, REJECTED_AT)).isTrue();
    }

    @Test
    void 타의로_회수된_오퍼는_즉시_허용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.WITHDRAWN, REJECTED_AT);

        assertThat(policy.isEligible(candidate, REJECTED_AT)).isTrue();
    }

    @Test
    void 드리미_거절_cooldown보다_더_지나면_허용한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.DREAMI_REJECTED, REJECTED_AT);
        LocalDateTime evaluatedAt = REJECTED_AT.plusMinutes(5).plusSeconds(1);

        assertThat(policy.isEligible(candidate, evaluatedAt)).isTrue();
    }

    @Test
    void evaluatedAt보다_미래인_occurredAt이면_예외가_발생한다() {
        MatchingCandidate candidate = candidateWithInteraction(PreviousOfferOutcome.WITHDRAWN, REJECTED_AT);
        LocalDateTime evaluatedAt = REJECTED_AT.minusSeconds(1);

        Throwable thrown = catchThrowable(() -> policy.isEligible(candidate, evaluatedAt));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 각_cooldown이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new OutcomeCooldownOfferPolicy(
                DREAMI_REJECTION_COOLDOWN,
                null,
                DREAMI_EXPIRATION_COOLDOWN
        ));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 각_cooldown이_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new OutcomeCooldownOfferPolicy(
                DREAMI_REJECTION_COOLDOWN,
                BOORMI_REJECTION_COOLDOWN,
                Duration.ofMinutes(-1)
        ));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingCandidate candidateWithInteraction(
            PreviousOfferOutcome outcome,
            LocalDateTime occurredAt
    ) {
        PreviousOfferInteraction interaction =
                new PreviousOfferInteraction(outcome, occurredAt);
        return candidateWithPreviousInteraction(Optional.of(interaction));
    }

    private MatchingCandidate candidateWithPreviousInteraction(Optional<PreviousOfferInteraction> previousInteraction) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), 0L, Duration.ZERO, Duration.ZERO, 0, 0, previousInteraction);
    }
}
