package com.naengsam.quick.domain.payment.dto;

import java.time.LocalDateTime;

/**
 * 지갑 거래 내역 한 줄. 포인트 원장과 머니 원장을 한 목록으로 합쳐 보여주기 위한 공통 표현이다.
 * <p>
 * {@code amount} 는 부호 있는 잔액 변동값이라 화면은 이 부호만으로 입금/출금을 가른다. 아이콘·제목 같은 화면 문구는 {@code txType}(PointTxTypeCd 또는
 * MoneyTxTypeCd 의 이름) 으로 클라이언트가 만든다.
 */
public record WalletTransactionDto(
        WalletTypeCd walletType,
        String txType,
        long amount,
        long balanceAfter,
        LocalDateTime createdDtm
) {
    public static WalletTransactionDto from(PointTransactionRow row) {
        return new WalletTransactionDto(WalletTypeCd.POINT, row.type().name(), row.amount(),
                row.balanceAfter(), row.createdDtm());
    }

    public static WalletTransactionDto from(MoneyTransactionRow row) {
        return new WalletTransactionDto(WalletTypeCd.MONEY, row.type().name(), row.amount(),
                row.balanceAfter(), row.createdDtm());
    }
}
