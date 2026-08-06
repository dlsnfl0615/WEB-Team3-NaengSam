package com.naengsam.quick.domain.upload.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * presigned URL이 발급된 용도. {@link com.naengsam.quick.domain.upload.entity.UploadSession}에 저장돼 다른 용도로 발급된 key를 재사용하지 못하게
 * 막는 데 쓰인다(실제 검증은 세션 row 대조로 이뤄지고, key 문자열에도 새겨 S3 상에서 용도별로 구분해볼 수 있게 한다). {@code resourceScopeRequired}가 true인 용도는
 * 건(주문/배송 등)마다 새로 발급받아야 하므로 resourceId가 필수다.
 */
@Getter
@RequiredArgsConstructor
public enum UploadPurpose {
    ORDER_ITEM_IMAGE(false),
    DREAMI_ID_CARD(false),
    DREAMI_CRIMINAL_RECORD(false),
    PICKUP_CERTIFICATION_IMAGE(true),
    DELIVERY_CERTIFICATION_IMAGE(true),
    ORDER_ITEM_IMAGE(false);

    private final boolean resourceScopeRequired;
}
