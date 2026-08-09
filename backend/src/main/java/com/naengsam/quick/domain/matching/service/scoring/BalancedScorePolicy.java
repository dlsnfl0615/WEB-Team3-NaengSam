package com.naengsam.quick.domain.matching.service.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;

/**
 * 거리·주문 대기시간·드리미 대기시간을 가중합해 점수를 계산하는 정책.
 * <p>각 요소는 기준값(상한/목표값) 대비 비율로 정규화한 뒤 가중치를 곱해 합산한다. 거리 비율은 점수를 높이고,
 * 대기시간 비율은 오래 기다릴수록 점수를 낮춰 우선순위를 높인다.
 */
public class BalancedScorePolicy implements MatchingScorePolicy {

    private static final long SCALE = 1000L;

    private final BalancedScoreWeights weights;
    private final long maxMatchingDistance;
    private final Duration targetOrderWait;
    private final Duration targetDreamiWait;

    public BalancedScorePolicy(BalancedScoreWeights weights, long maxMatchingDistance,
            Duration targetOrderWait, Duration targetDreamiWait) {
        if (weights == null) {
            throw new IllegalArgumentException("weights는 null일 수 없습니다.");
        }
        if (maxMatchingDistance <= 0) {
            throw new IllegalArgumentException("maxMatchingDistance는 0보다 커야 합니다: " + maxMatchingDistance);
        }
        if (targetOrderWait == null || targetOrderWait.isZero() || targetOrderWait.isNegative()) {
            throw new IllegalArgumentException("targetOrderWait는 0보다 커야 합니다: " + targetOrderWait);
        }
        if (targetDreamiWait == null || targetDreamiWait.isZero() || targetDreamiWait.isNegative()) {
            throw new IllegalArgumentException("targetDreamiWait는 0보다 커야 합니다: " + targetDreamiWait);
        }
        this.weights = weights;
        this.maxMatchingDistance = maxMatchingDistance;
        this.targetOrderWait = targetOrderWait;
        this.targetDreamiWait = targetDreamiWait;
    }

    @Override
    public long score(MatchingCandidate candidate) {
        double distanceRatio = ratio(candidate.distanceMeters(), maxMatchingDistance);
        double orderWaitRatio = ratio(candidate.orderWaitingTime().toMillis(), targetOrderWait.toMillis());
        double dreamiWaitRatio = ratio(candidate.dreamiWaitingTime().toMillis(), targetDreamiWait.toMillis());

        double weighted = weights.distanceWeight() * distanceRatio
                - weights.orderWaitWeight() * orderWaitRatio
                - weights.dreamiWaitWeight() * dreamiWaitRatio;

        return Math.round(weighted / weights.totalWeight() * SCALE);
    }

    private double ratio(long value, long base) {
        return Math.min(1.0, value / (double) base);
    }
}
