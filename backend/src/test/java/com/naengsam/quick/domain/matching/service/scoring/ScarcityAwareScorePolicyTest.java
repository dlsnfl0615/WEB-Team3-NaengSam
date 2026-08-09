package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ScarcityAwareScorePolicy가 기본 점수에 주문·드리미 희소성을 반영해 우선순위를 조정하는지 확인한다.
 */
class ScarcityAwareScorePolicyTest {

    private final DistanceOnlyScorePolicy baseScorePolicy = new DistanceOnlyScorePolicy();
    private final ScarcityAwareScorePolicy policy =
            new ScarcityAwareScorePolicy(baseScorePolicy, new ScarcityScoreWeights(10, 45, 45));

    @Test
    void 후보_드리미가_적은_주문이_우선한다() {
        MatchingCandidate scarceOrder = candidate(500L, 1, 5);
        MatchingCandidate abundantOrder = candidate(500L, 10, 5);

        assertThat(policy.score(scarceOrder)).isLessThan(policy.score(abundantOrder));
    }

    @Test
    void 여러_주문에_배정_가능한_드리미는_점수가_높아_보존된다() {
        MatchingCandidate flexibleDreami = candidate(500L, 5, 10);
        MatchingCandidate scarceDreami = candidate(500L, 5, 1);

        assertThat(policy.score(flexibleDreami)).isGreaterThan(policy.score(scarceDreami));
    }

    @Test
    void 후보_수가_0이면_1일_때보다_희소성_보정이_더_크다() {
        MatchingCandidate zeroCandidates = candidate(500L, 0, 5);
        MatchingCandidate oneCandidate = candidate(500L, 1, 5);

        assertThat(policy.score(zeroCandidates)).isLessThan(policy.score(oneCandidate));
    }

    @Test
    void 후보_수가_0이어도_예외없이_점수를_계산한다() {
        MatchingCandidate candidate = candidate(500L, 0, 0);

        assertThat(policy.score(candidate)).isNotNull();
    }

    @Test
    void 기본_점수가_같아도_희소성이_다르면_결과가_달라진다() {
        MatchingCandidate sameDistanceScarce = candidate(500L, 1, 1);
        MatchingCandidate sameDistanceAbundant = candidate(500L, 10, 10);

        assertThat(baseScorePolicy.score(sameDistanceScarce)).isEqualTo(baseScorePolicy.score(sameDistanceAbundant));
        assertThat(policy.score(sameDistanceScarce)).isNotEqualTo(policy.score(sameDistanceAbundant));
        assertThat(policy.score(sameDistanceScarce)).isLessThan(policy.score(sameDistanceAbundant));
    }

    @Test
    void baseScorePolicy가_null이면_예현외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new ScarcityAwareScorePolicy(null, new ScarcityScoreWeights(10, 45, 45)));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weights가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new ScarcityAwareScorePolicy(baseScorePolicy, null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingCandidate candidate(long distanceMeters, int orderCandidateCount, int dreamiCandidateCount) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), distanceMeters,
                Duration.ZERO, Duration.ZERO, orderCandidateCount, dreamiCandidateCount);
    }
}
