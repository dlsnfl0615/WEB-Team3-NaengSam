package com.naengsam.quick.domain.payment.dto;

import com.naengsam.quick.domain.payment.entity.PaymentCd;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 충전 요청. 포인트와 원은 1:1 이라 {@code amount} 가 결제 금액이자 적립될 포인트다.
 */
public record PointChargeRequest(
        @NotNull
        @Min(1000)
        @Max(1000000)
        Long amount,

        @NotNull
        PaymentCd paymentCd
) {
}
