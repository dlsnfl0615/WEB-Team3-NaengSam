package com.naengsam.quick.domain.matching.policy.scope;

/**
 * 주문이 오퍼를 낼 수 있는 드리미 탐색 반경. 주문 대기시간이 길어질수록 {@link OfferScopeResolver}가 더 넓은
 * radiusMeters를 가진 scope를 골라 후보 풀을 넓힌다.
 */
public record OfferScope(long radiusMeters) {
    public OfferScope {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters는 0보다 커야 합니다: " + radiusMeters);
        }
    }
}
