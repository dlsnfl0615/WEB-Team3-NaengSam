package com.naengsam.quick.domain.matching.policy.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidateView;

/**
 * 기본 거리·대기 점수에 희소성을 더해 우선순위를 조정하는 정책.
 * <p>이 주문을 수행할 수 있는 드리미가 적을수록(주문 희소성), 또는 이 드리미가 수행할 수 있는 주문이 적을수록(드리미 희소성)
 * 지금 매칭하지 않으면 기회를 잃을 위험이 크므로 점수를 낮춰 우선순위를 높인다. 반대로 여러 주문에 배정 가능한 드리미는 점수를 높여 다른 주문을 위해 보존한다.
 */
public class ScarcityAwareScorePolicy implements MatchingScorePolicy {

    private static final long SCALE = 1000L;

    private final MatchingScorePolicy baseScorePolicy;
    private final ScarcityScoreWeights weights;

    public ScarcityAwareScorePolicy(MatchingScorePolicy baseScorePolicy, ScarcityScoreWeights weights) {
        if (baseScorePolicy == null) {
            throw new IllegalArgumentException("baseScorePolicy는 null일 수 없습니다.");
        }
        if (weights == null) {
            throw new IllegalArgumentException("weights는 null일 수 없습니다.");
        }
        this.baseScorePolicy = baseScorePolicy;
        this.weights = weights;
    }

    @Override
    public long score(MatchingCandidateView candidate) {
        long baseScore = baseScorePolicy.score(candidate);
        long orderScarcity = SCALE / (candidate.orderCandidateCount() + 1);
        long dreamiScarcity = SCALE / (candidate.dreamiCandidateCount() + 1);

        long weighted = weights.baseWeight() * baseScore
                - weights.orderScarcityWeight() * orderScarcity
                - weights.dreamiScarcityWeight() * dreamiScarcity;

        return weighted / weights.totalWeight();
    }
}
