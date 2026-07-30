package com.naengsam.quick.domain.delivery.dto;

import java.math.BigDecimal;

/**
 * 드리미의 최신 위치 응답.
 */
public record DeliveryLocationDto(
        BigDecimal latitude,
        BigDecimal longitude
) {
}
