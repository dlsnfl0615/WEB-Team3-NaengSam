package com.naengsam.quick.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 머니 → 포인트 전환 요청. 전환 비율은 1:1 이고 수수료는 없다.
 */
public record ExchangeRequest(
        @NotNull
        @Min(1000)
        Long amount
) {
}
