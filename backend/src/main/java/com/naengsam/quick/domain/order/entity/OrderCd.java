package com.naengsam.quick.domain.order.entity;

public enum OrderCd {
    MATCHING, // 매칭 중
    PENDING_BOORMI_CONFIRMATION, // 드리미가 수락하고 부르미가 수락할 때까지 대기
    IN_PROGRESS, // 배달(또는 픽업) 중
    WAITING_CONFIRMATION, // 사용 X
    COMPLETED, // 배달 완료
    CANCELLED, // 취소됨 (픽업 전 취소, 픽업 후 취소, 배달 완료 후 취소 등)
    CLAIM_REVIEW // 클레임 리뷰 중
}
