package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * BalancedScorePolicy가 거리·주문 대기시간·드리미 대기시간을 가중치대로 정규화·합산하는지 확인한다.
 */
class BalancedScorePolicyTest {

    private static final long MAX_MATCHING_DISTANCE = 1000L;
    private static final Duration TARGET_ORDER_WAIT = Duration.ofMinutes(10);
    private static final Duration TARGET_DREAMI_WAIT = Duration.ofMinutes(20);

    private final BalancedScorePolicy policy = new BalancedScorePolicy(
            new BalancedScoreWeights(65, 25, 10), MAX_MATCHING_DISTANCE, TARGET_ORDER_WAIT, TARGET_DREAMI_WAIT);

    @Test
    void 기본_가중치로_거리_대기시간을_정규화해_가중합한다() {
        MatchingCandidate candidate = candidate(500L, Duration.ofMinutes(5), Duration.ofMinutes(10));

        long expected = Math.round((65 * 0.5 - 25 * 0.5 - 10 * 0.5) / 100.0 * 1000);
        assertThat(policy.score(candidate)).isEqualTo(expected);
    }

    @Test
    void 거리가_상한을_넘으면_비율이_1로_고정된다() {
        MatchingCandidate atCap = candidate(MAX_MATCHING_DISTANCE, Duration.ZERO, Duration.ZERO);
        MatchingCandidate overCap = candidate(MAX_MATCHING_DISTANCE * 10, Duration.ZERO, Duration.ZERO);

        assertThat(policy.score(overCap)).isEqualTo(policy.score(atCap));
    }

    @Test
    void 주문_대기시간이_목표치를_넘으면_비율이_1로_고정된다() {
        MatchingCandidate atTarget = candidate(0L, TARGET_ORDER_WAIT, Duration.ZERO);
        MatchingCandidate overTarget = candidate(0L, TARGET_ORDER_WAIT.multipliedBy(10), Duration.ZERO);

        assertThat(policy.score(overTarget)).isEqualTo(policy.score(atTarget));
    }

    @Test
    void 가중치_합이_100이_아니어도_합으로_정규화해_계산한다() {
        MatchingCandidate candidate = candidate(500L, Duration.ofMinutes(5), Duration.ofMinutes(10));
        BalancedScorePolicy unnormalizedWeightPolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(130, 50, 20), MAX_MATCHING_DISTANCE, TARGET_ORDER_WAIT, TARGET_DREAMI_WAIT);

        long expected = Math.round((130 * 0.5 - 50 * 0.5 - 20 * 0.5) / 200.0 * 1000);
        assertThat(unnormalizedWeightPolicy.score(candidate)).isEqualTo(expected);
    }

    @Test
    void 가중치가_다르면_같은_후보라도_점수가_달라진다() {
        MatchingCandidate candidate = candidate(500L, Duration.ofMinutes(5), Duration.ofMinutes(10));
        BalancedScorePolicy distanceHeavyPolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(90, 5, 5), MAX_MATCHING_DISTANCE, TARGET_ORDER_WAIT, TARGET_DREAMI_WAIT);

        assertThat(distanceHeavyPolicy.score(candidate)).isNotEqualTo(policy.score(candidate));
    }

    @Test
    void 가중치_합이_0이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScoreWeights(0, 0, 0));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가중치_합이_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScoreWeights(-10, -20, 5));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가중치가_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScoreWeights(-10, 60, 50));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxMatchingDistance가_0이하이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScorePolicy(
                new BalancedScoreWeights(65, 25, 10), 0L, TARGET_ORDER_WAIT, TARGET_DREAMI_WAIT));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetOrderWait가_0이하이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScorePolicy(
                new BalancedScoreWeights(65, 25, 10), MAX_MATCHING_DISTANCE, Duration.ZERO, TARGET_DREAMI_WAIT));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetDreamiWait가_0이하이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new BalancedScorePolicy(
                new BalancedScoreWeights(65, 25, 10), MAX_MATCHING_DISTANCE, TARGET_ORDER_WAIT, Duration.ZERO));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingCandidate candidate(long distanceMeters, Duration orderWaitingTime, Duration dreamiWaitingTime) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), distanceMeters,
                orderWaitingTime, dreamiWaitingTime, 0, 0, Optional.empty());
    }
}
