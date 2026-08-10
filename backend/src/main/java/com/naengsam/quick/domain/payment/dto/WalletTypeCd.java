package com.naengsam.quick.domain.payment.dto;

/**
 * 거래 내역이 어느 지갑에서 나왔는지. 화면의 단위 표기(P / ₩)가 이 값으로 갈린다.
 */
public enum WalletTypeCd {
    POINT,
    MONEY
}
