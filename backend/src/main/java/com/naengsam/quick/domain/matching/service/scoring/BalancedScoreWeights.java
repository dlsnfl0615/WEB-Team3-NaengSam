package com.naengsam.quick.domain.matching.service.scoring;

/**
 * {@link BalancedScorePolicy}가 사용하는 거리·주문 대기시간·드리미 대기시간 가중치. {@link BalancedScorePolicy}가 합으로
 * 나누어 정규화하므로 세 값의 합이 100일 필요는 없다.
 */
public record BalancedScoreWeights(int distanceWeight, int orderWaitWeight, int dreamiWaitWeight) {
    public BalancedScoreWeights {
        if (distanceWeight < 0 || orderWaitWeight < 0 || dreamiWaitWeight < 0) {
            throw new IllegalArgumentException(
                    "가중치는 음수일 수 없습니다: " + distanceWeight + ", " + orderWaitWeight + ", " + dreamiWaitWeight);
        }
        if (totalWeight(distanceWeight, orderWaitWeight, dreamiWaitWeight) <= 0) {
            throw new IllegalArgumentException("가중치의 합은 0보다 커야 합니다.");
        }
    }

    public int totalWeight() {
        return totalWeight(distanceWeight, orderWaitWeight, dreamiWaitWeight);
    }

    private static int totalWeight(int distanceWeight, int orderWaitWeight, int dreamiWaitWeight) {
        return distanceWeight + orderWaitWeight + dreamiWaitWeight;
    }
}
