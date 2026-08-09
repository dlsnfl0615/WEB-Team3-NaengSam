package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SlaUrgencyScorePolicy가 SLA 초과 위험이 커질수록 대기시간을 거리보다 우선하는지 확인한다.
 */
class SlaUrgencyScorePolicyTest {

    private static final long MAX_MATCHING_DISTANCE = 1000L;
    private static final Duration SLA_THRESHOLD = Duration.ofMinutes(10);
    private static final long URGENCY_WEIGHT = 500L;

    private final SlaUrgencyScorePolicy policy =
            new SlaUrgencyScorePolicy(MAX_MATCHING_DISTANCE, SLA_THRESHOLD, URGENCY_WEIGHT);

    @Test
    void SLA에서_먼_주문은_거리_점수를_그대로_반영한다() {
        MatchingCandidate near = candidate(100L, Duration.ZERO);
        MatchingCandidate far = candidate(1000L, Duration.ZERO);

        assertThat(policy.score(near)).isEqualTo(100L);
        assertThat(policy.score(far)).isEqualTo(1000L);
    }

    @Test
    void SLA에_가까운_주문은_거리가_불리해도_우선순위가_더_높다() {
        MatchingCandidate closeToSlaWithBadDistance = candidate(MAX_MATCHING_DISTANCE, SLA_THRESHOLD);
        MatchingCandidate farFromSlaWithGoodDistance = candidate(0L, Duration.ZERO);

        assertThat(policy.score(closeToSlaWithBadDistance)).isLessThan(policy.score(farFromSlaWithGoodDistance));
    }

    @Test
    void 대기시간이_늘어날수록_점수는_비선형으로_급격히_낮아진다() {
        MatchingCandidate quarter = candidate(500L, SLA_THRESHOLD.dividedBy(4));
        MatchingCandidate half = candidate(500L, SLA_THRESHOLD.dividedBy(2));
        MatchingCandidate full = candidate(500L, SLA_THRESHOLD);

        long firstDrop = policy.score(quarter) - policy.score(half);
        long secondDrop = policy.score(half) - policy.score(full);

        assertThat(secondDrop).isGreaterThan(firstDrop);
    }

    @Test
    void SLA를_초과한_대기시간은_상한으로_처리되어_점수가_더_이상_변하지_않는다() {
        MatchingCandidate atSla = candidate(500L, SLA_THRESHOLD);
        MatchingCandidate overSla = candidate(500L, SLA_THRESHOLD.multipliedBy(10));

        assertThat(policy.score(overSla)).isEqualTo(policy.score(atSla));
    }

    @Test
    void 거리와_대기시간이_모두_0이면_점수는_0이다() {
        MatchingCandidate candidate = candidate(0L, Duration.ZERO);

        assertThat(policy.score(candidate)).isZero();
    }

    @Test
    void 거리와_대기시간이_모두_상한이면_정해진_최소값을_반환한다() {
        MatchingCandidate candidate = candidate(MAX_MATCHING_DISTANCE, SLA_THRESHOLD);

        long expected = 1000L - URGENCY_WEIGHT * 1000L;
        assertThat(policy.score(candidate)).isEqualTo(expected);
    }

    @Test
    void maxMatchingDistance가_0이하이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new SlaUrgencyScorePolicy(0L, SLA_THRESHOLD, URGENCY_WEIGHT));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slaThreshold가_0이하이면_예외가_발생한다() {
        Throwable thrown =
                catchThrowable(() -> new SlaUrgencyScorePolicy(MAX_MATCHING_DISTANCE, Duration.ZERO, URGENCY_WEIGHT));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urgencyWeight가_0이하이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new SlaUrgencyScorePolicy(MAX_MATCHING_DISTANCE, SLA_THRESHOLD, 0L));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingCandidate candidate(long distanceMeters, Duration orderWaitingTime) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), distanceMeters,
                orderWaitingTime, Duration.ZERO, 0, 0);
    }
}
