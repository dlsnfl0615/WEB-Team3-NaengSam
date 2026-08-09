package com.naengsam.quick.domain.matching.service.scoring;

/**
 * {@link ScarcityAwareScorePolicy}가 사용하는 기본 점수·주문 희소성·드리미 희소성 가중치. {@link ScarcityAwareScorePolicy}가
 * 합으로 나누어 정규화하므로 세 값의 합이 100일 필요는 없다.
 */
public record ScarcityScoreWeights(int baseWeight, int orderScarcityWeight, int dreamiScarcityWeight) {
    public ScarcityScoreWeights {
        if (baseWeight < 0 || orderScarcityWeight < 0 || dreamiScarcityWeight < 0) {
            throw new IllegalArgumentException(
                    "가중치는 음수일 수 없습니다: " + baseWeight + ", " + orderScarcityWeight + ", " + dreamiScarcityWeight);
        }
        if (totalWeight(baseWeight, orderScarcityWeight, dreamiScarcityWeight) <= 0) {
            throw new IllegalArgumentException("가중치의 합은 0보다 커야 합니다.");
        }
    }

    public int totalWeight() {
        return totalWeight(baseWeight, orderScarcityWeight, dreamiScarcityWeight);
    }

    private static int totalWeight(int baseWeight, int orderScarcityWeight, int dreamiScarcityWeight) {
        return baseWeight + orderScarcityWeight + dreamiScarcityWeight;
    }
}
