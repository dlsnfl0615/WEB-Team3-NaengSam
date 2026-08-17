package com.naengsam.quick.domain.matching.policy.scope;

/**
 * 주문이 오퍼를 낼 수 있는 드리미 최대 픽업거리. 주문 대기시간이 길어질수록 {@link OfferScopeResolver}가 더 넓은
 * maxPickupDistanceMeters를 가진 scope를 골라 후보 풀을 넓힌다.
 */
public record OfferScope(long maxPickupDistanceMeters) {
    public OfferScope {
        if (maxPickupDistanceMeters <= 0) {
            throw new IllegalArgumentException(
                    "maxPickupDistanceMeters는 0보다 커야 합니다: " + maxPickupDistanceMeters);
        }
    }
}
