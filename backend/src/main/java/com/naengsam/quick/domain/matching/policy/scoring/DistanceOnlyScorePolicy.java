package com.naengsam.quick.domain.matching.policy.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import org.springframework.stereotype.Component;

/**
 * 거리만으로 점수를 매기는 정책. score = distanceMeters.
 */
@Component
public class DistanceOnlyScorePolicy implements MatchingScorePolicy {

    @Override
    public long score(MatchingCandidate candidate) {
        return candidate.distanceMeters();
    }
}
