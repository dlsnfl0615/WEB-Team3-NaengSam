package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.scoring.DistanceOnlyScorePolicy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DistanceOnlyScorePolicy가 거리만으로 점수를 계산하는지 확인한다.
 */
class DistanceOnlyScorePolicyTest {

    private final DistanceOnlyScorePolicy policy = new DistanceOnlyScorePolicy();

    @Test
    void 가까운_후보의_점수가_낮다() {
        MatchingCandidate near = candidateWithDistance(100L);
        MatchingCandidate far = candidateWithDistance(500L);

        assertThat(policy.score(near)).isLessThan(policy.score(far));
    }

    @Test
    void 거리가_같으면_점수가_같다() {
        MatchingCandidate first = candidateWithDistance(300L);
        MatchingCandidate second = candidateWithDistance(300L);

        assertThat(policy.score(first)).isEqualTo(policy.score(second));
    }

    @Test
    void 거리가_0이면_점수도_0이다() {
        MatchingCandidate candidate = candidateWithDistance(0L);

        assertThat(policy.score(candidate)).isZero();
    }

    private MatchingCandidate candidateWithDistance(long distanceMeters) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), distanceMeters,
                Duration.ZERO, Duration.ZERO, 0, 0, Optional.empty());
    }
}
