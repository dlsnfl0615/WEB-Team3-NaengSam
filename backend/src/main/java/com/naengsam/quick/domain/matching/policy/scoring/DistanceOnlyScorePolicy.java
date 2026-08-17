package com.naengsam.quick.domain.matching.policy.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidateView;

/**
 * 거리만으로 점수를 매기는 정책. score = distanceMeters.
 */
public class DistanceOnlyScorePolicy implements MatchingScorePolicy {

    @Override
    public long score(MatchingCandidateView candidate) {
        return Math.round(candidate.distanceMeters());
    }
}
