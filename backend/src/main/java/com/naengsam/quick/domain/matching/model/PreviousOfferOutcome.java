package com.naengsam.quick.domain.matching.model;

/**
 * 같은 주문-드리미 조합의 과거 오퍼가 어떻게 끝났는지를 나타낸다.
 */
public enum PreviousOfferOutcome {
    DREAMI_REJECTED,
    BOORMI_REJECTED,
    DREAMI_EXPIRED,
    BOORMI_EXPIRED,
    WITHDRAWN
}
