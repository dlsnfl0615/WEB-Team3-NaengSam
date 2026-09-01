package com.naengsam.quick.domain.upload.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * presigned URL이 발급된 용도. {@link com.naengsam.quick.domain.upload.entity.UploadSession}에 저장돼 다른 용도로 발급된 key를 재사용하지 못하게
 * 막는 데 쓰인다(실제 검증은 세션 row 대조로 이뤄지고, key 문자열에도 새겨 S3 상에서 용도별로 구분해볼 수 있게 한다). {@code resourceScopeRequired}가 true인 용도는
 * 건(주문/배송 등)마다 새로 발급받아야 하므로 resourceId가 필수다.
 */
@Getter  // Lombok: enum 필드(resourceScopeRequired)의 getter를 자동 생성
@RequiredArgsConstructor  // Lombok: final 필드를 인수로 받는 생성자를 자동 생성. Java enum은 생성자를 가질 수 있음 → annotations.md
public enum UploadPurpose {
    ORDER_ITEM_IMAGE(false), // 부르미가 주문 생성 중 올리는 물건 사진(아직 orderId가 없어 resourceId 불필요)
    DREAMI_ID_CARD(false),
    DREAMI_CRIMINAL_RECORD(false),
    PICKUP_CERTIFICATION_IMAGE(true),  // true/false: 상수 선언 시 생성자 인수 전달. Java enum은 클래스처럼 필드·생성자·메서드를 가질 수 있음
    DELIVERY_CERTIFICATION_IMAGE(true);

    private final boolean resourceScopeRequired;  // 상수별 추가 데이터. enum에서 final 필드는 각 상수의 고유 값을 저장
}
