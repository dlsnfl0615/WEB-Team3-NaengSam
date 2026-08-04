package com.naengsam.quick.domain.delivery.entity;

// 실제 ERD Cloud 기준으로 작성된 상태
// todo : 인메모리에서는 DeliveryStatusCd를 사용하지 않고, DeliveryCd를 사용 중임 (필요시 통합 필요)
public enum DeliveryStatusCd {
    PICKING_UP,                // 픽업중
    DELIVERING,                // 배달중
    DELAYED,                   // 지연
    SUSPENDED,                 // 중단
    PARTNER_HANDOFF_PENDING,   // 파트너 인계 대기
    TRANSFERRED_TO_PARTNER,    // 파트너 인계 완료
    RETURNING,                 // 반송중
    RETURNED,                  // 반송 완료
    COMPLETED,                 // 배달 완료
    TERMINATED                 // 종료
}
