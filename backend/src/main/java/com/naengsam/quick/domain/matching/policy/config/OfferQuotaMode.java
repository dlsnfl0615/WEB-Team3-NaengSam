package com.naengsam.quick.domain.matching.policy.config;

/**
 * {@code matching.offer-quota-mode} 로 선택하는 주문당 최대 동시 오퍼 수(maxConcurrentOffers) 산정 방식.
 */
public enum OfferQuotaMode {
    /**
     * {@link MatchingPolicyProperties#maxConcurrentOffers()}를 모든 주문에 고정값으로 적용한다.
     */
    FIXED,
    /**
     * 배치 시점의 대기 드리미 수/대기 주문 수 비율로 quota를 매 배치마다 다시 계산한다.
     */
    DYNAMIC
}
