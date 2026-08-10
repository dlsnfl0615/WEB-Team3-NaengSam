package com.naengsam.quick.domain.payment.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum PaymentErrorCode implements BaseErrorCode {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "지갑을 찾을 수 없습니다."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "PAYMENT_002", "포인트 잔액이 부족해요."),
    ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_003", "이미 결제된 요청이에요."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_004", "결제 내역을 찾을 수 없습니다."),
    INSUFFICIENT_MONEY(HttpStatus.BAD_REQUEST, "PAYMENT_005", "머니 잔액이 부족해요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
