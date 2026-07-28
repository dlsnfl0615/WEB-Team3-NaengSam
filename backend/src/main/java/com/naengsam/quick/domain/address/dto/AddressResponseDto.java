package com.naengsam.quick.domain.address.dto;

/**
 * 저장된 배송지 조회 응답.
 */
public record AddressResponseDto(
        String addressAlias,
        String addressLine1,
        String addressLine2,
        String boormiId
) {
}
