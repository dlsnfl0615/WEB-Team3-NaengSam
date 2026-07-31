package com.naengsam.quick.domain.delivery.entity;

public enum DeliveryCd {
    PICKUP_NORMAL,                // 픽업중_정상
    PICKUP_CANCELLED_BY_BOORMI,   // 픽업중_부르미의_취소
    PICKUP_CANCELLED_BY_DREAMI,   // 픽업중_드리미의_취소
    PICKUP_CANCELLED_BY_ADMIN,    // 픽업중_관리자의_취소
    DELIVERING,                   // 배달중_정상 (배달 시작 후 취소 불가 가정)
    DELIVERED                     // 배달_완료 (사진 완료 시 전이)
}
