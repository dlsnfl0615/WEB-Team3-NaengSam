package com.naengsam.quick.domain.delivery.entity;

public enum DeliveryCd {
    PICKUP_NORMAL,                // 픽업중_정상
    PICKUP_DELAYED,               // 픽업 중 지연
    // CANCELLED 상태 추가시 cancelBy() 수정 필요
    PICKUP_CANCELLED_BY_BOORMI,   // 픽업중_부르미의_취소
    PICKUP_CANCELLED_BY_DREAMI,   // 픽업중_드리미의_취소
    PICKUP_CANCELLED_BY_ADMIN,    // 픽업중_관리자의_취소
    DELIVERING,                   // 배달중_정상 (배달 시작 후 취소 불가 가정)
    DELIVERED,                    // 배달_완료 (사진 완료 시 전이)
    PARTNER_HANDOFF_PENDING,      // 파트너 인계 대기
    TRANSFERRED_TO_PARTNER,       // 파트너 인계 완료
    RETURNING,                    // 반송중
    RETURNED,                     // 반송 완료
    TERMINATED                    // 종료
}
