package com.naengsam.quick.domain.matching.service.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;

/**
 * SLA(주문 대기 한계) 초과 위험이 커질수록 거리보다 대기시간을 우선하는 정책.
 * <p>score = normalizedDistance - urgencyWeight * normalizedOrderWait^2. 부동소수점 오차를 피하기 위해
 * 정수(long) 연산만으로 계산하며, 거리·대기시간은 각각 상한 대비 비율을 0~SCALE 정수 값으로 정규화한다.
 */
public class SlaUrgencyScorePolicy implements MatchingScorePolicy {

    private static final long SCALE = 1000L;

    private final long maxMatchingDistance;
    private final Duration slaThreshold;
    private final long urgencyWeight;

    public SlaUrgencyScorePolicy(long maxMatchingDistance, Duration slaThreshold, long urgencyWeight) {
        if (maxMatchingDistance <= 0) {
            throw new IllegalArgumentException("maxMatchingDistance는 0보다 커야 합니다: " + maxMatchingDistance);
        }
        if (slaThreshold == null || slaThreshold.isZero() || slaThreshold.isNegative()) {
            throw new IllegalArgumentException("slaThreshold는 0보다 커야 합니다: " + slaThreshold);
        }
        if (urgencyWeight <= 0) {
            throw new IllegalArgumentException("urgencyWeight는 0보다 커야 합니다: " + urgencyWeight);
        }
        this.maxMatchingDistance = maxMatchingDistance;
        this.slaThreshold = slaThreshold;
        this.urgencyWeight = urgencyWeight;
    }

    @Override
    public long score(MatchingCandidate candidate) {
        long normalizedDistance = normalize(candidate.distanceMeters(), maxMatchingDistance);
        long normalizedOrderWait = normalize(candidate.orderWaitingTime().toMillis(), slaThreshold.toMillis());

        long urgencyPenalty = urgencyWeight * (normalizedOrderWait * normalizedOrderWait / SCALE);
        return normalizedDistance - urgencyPenalty;
    }

    private long normalize(long value, long base) {
        long clamped = Math.min(value, base);
        return clamped * SCALE / base;
    }
}
