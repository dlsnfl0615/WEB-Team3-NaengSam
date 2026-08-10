package com.naengsam.quick.domain.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 배송지 저장 요청. 좌표는 요청에 포함하지 않고, 서버가 {@code addressLine1} 을 좌표 변환해 채운다.
 */
public record AddressRequestDto(
        @NotBlank
        @Size(max = 50)
        String addressAlias,

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @NotBlank
        @Size(max = 255)
        String addressLine2
) {
}
