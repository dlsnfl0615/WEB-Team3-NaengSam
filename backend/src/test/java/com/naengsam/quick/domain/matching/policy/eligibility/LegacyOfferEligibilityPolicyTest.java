package com.naengsam.quick.domain.matching.policy.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * LegacyOfferEligibilityPolicy가 legacy MatchOffer.shouldExcludeFromRematch()의 제외 규칙을 그대로
 * 재현하는지 확인한다.
 */
class LegacyOfferEligibilityPolicyTest {

    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 9, 8, 0);

    private final LegacyOfferEligibilityPolicy policy = new LegacyOfferEligibilityPolicy();

    @Test
    void 이전_이력이_없으면_적격하다() {
        MatchingCandidate candidate = candidateWithPreviousInteraction(Optional.empty());

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isTrue();
    }

    @Test
    void 드리미가_거절했으면_부적격하다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.DREAMI_REJECTED);

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isFalse();
    }

    @Test
    void 부르미가_거절했으면_부적격하다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.BOORMI_REJECTED);

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isFalse();
    }

    @Test
    void 드리미_응답_timeout이면_부적격하다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.DREAMI_EXPIRED);

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isFalse();
    }

    @Test
    void 회수된_제안이면_적격하다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.WITHDRAWN);

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isTrue();
    }

    @Test
    void 부르미_응답_timeout이면_적격하다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.BOORMI_EXPIRED);

        assertThat(policy.isEligible(candidate, EVALUATED_AT)).isTrue();
    }

    @Test
    void 같은_후보와_평가_시각이면_항상_같은_결과를_반환한다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.DREAMI_REJECTED);

        boolean first = policy.isEligible(candidate, EVALUATED_AT);
        boolean second = policy.isEligible(candidate, EVALUATED_AT);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void evaluatedAt이_달라도_결과는_바뀌지_않는다() {
        MatchingCandidate candidate = candidateWithOutcome(PreviousOfferOutcome.WITHDRAWN);

        boolean atEvaluatedAt = policy.isEligible(candidate, EVALUATED_AT);
        boolean atMuchLaterTime = policy.isEligible(candidate, EVALUATED_AT.plusYears(10));

        assertThat(atEvaluatedAt).isEqualTo(atMuchLaterTime);
    }

    private MatchingCandidate candidateWithOutcome(PreviousOfferOutcome outcome) {
        return candidateWithPreviousInteraction(Optional.of(new PreviousOfferInteraction(outcome, OCCURRED_AT)));
    }

    private MatchingCandidate candidateWithPreviousInteraction(Optional<PreviousOfferInteraction> previousInteraction) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), 0L, Duration.ZERO, Duration.ZERO, 0, 0, previousInteraction);
    }
}
