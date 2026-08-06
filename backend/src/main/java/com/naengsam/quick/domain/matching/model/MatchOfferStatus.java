package com.naengsam.quick.domain.matching.model;

public enum MatchOfferStatus {
    /**
     * 드리미에게 제안이 전달되어 응답 대기 중
     */
    OFFERED,
    /**
     * 해당 드리미가 수락하여 부르미의 승낙을 대기중
     */
    PENDING_BOORMI_CONFIRMATION,
    /**
     * 해당 드리미가 수락했고 부르미도 수락해 매칭 후보로 확정됨
     */
    MATCHED,
    /**
     * 해당 부르미가 명시적으로 거절함
     */
    BOORMI_REJECTED,
    /**
     * 해당 드리미가 명시적으로 거절함
     */
    DREAMI_REJECTED,
    /**
     * 제한 시간 내 부르미가 응답하지 않아 만료됨
     */
    BOORMI_EXPIRED,
    /**
     * 제한 시간 내 드리미가 응답하지 않아 만료됨
     */
    DREAMI_EXPIRED,
    /**
     * 다른 드리미가 먼저 수락했거나 서버가 제안을 회수함
     */
    WITHDRAWN
}
