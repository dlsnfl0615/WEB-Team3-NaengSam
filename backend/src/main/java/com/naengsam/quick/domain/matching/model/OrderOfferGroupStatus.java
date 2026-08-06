package com.naengsam.quick.domain.matching.model;

public enum OrderOfferGroupStatus {
    /**
     * 제안 응답 대기 중 (드리미 수락 후 부르미 확인 대기 포함)
     */
    OPEN,
    /**
     * 드리미+부르미 모두 수락하여 매칭 확정
     */
    MATCHED,
    /**
     * 더 이상 유효한 제안이 없음. rematchRequired로 재매칭 필요 여부를 판단한다.
     */
    CLOSED
}
