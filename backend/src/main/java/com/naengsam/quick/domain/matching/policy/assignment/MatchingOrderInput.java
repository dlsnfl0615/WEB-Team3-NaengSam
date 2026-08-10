package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.time.Duration;
import java.util.UUID;

/**
 * 배정 알고리즘에 입력되는 주문 정보. maxConcurrentOffers는 이 주문에 동시에 제안을 뿌릴 최대 드리미 수(선착순 수락
 * 대상 후보 수)를 뜻하며 1 이상이어야 한다. 같은 orderId로 여러 {@link MatchingProposal}이 나올 수 있고, 그중
 * 실제로 수락되는 건 이후 오퍼 절차(수락/거절)에서 결정된다.
 */
public record MatchingOrderInput(UUID orderId, GeoPoint location, Duration waitingTime, int maxConcurrentOffers) {
    public MatchingOrderInput {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId는 null일 수 없습니다.");
        }
        if (location == null) {
            throw new IllegalArgumentException("location은 null일 수 없습니다.");
        }
        if (waitingTime == null || waitingTime.isNegative()) {
            throw new IllegalArgumentException("waitingTime은 null이거나 음수일 수 없습니다: " + waitingTime);
        }
        if (maxConcurrentOffers < 1) {
            throw new IllegalArgumentException("maxConcurrentOffers는 1 이상이어야 합니다: " + maxConcurrentOffers);
        }
    }
}
