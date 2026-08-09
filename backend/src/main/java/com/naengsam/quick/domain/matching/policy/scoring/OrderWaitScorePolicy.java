package com.naengsam.quick.domain.matching.policy.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;

/**
 * 주문이 오래 기다릴수록 우선 매칭되도록 하는 FIFO 성격의 기준선 정책. score = -orderWaitingMillis.
 */
public class OrderWaitScorePolicy implements MatchingScorePolicy {

    @Override
    public long score(MatchingCandidate candidate) {
        return -candidate.orderWaitingTime().toMillis();
    }
}
