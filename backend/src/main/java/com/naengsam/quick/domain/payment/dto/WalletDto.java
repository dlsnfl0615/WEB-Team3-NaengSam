package com.naengsam.quick.domain.payment.dto;

import java.util.List;

/**
 * 내 지갑 화면 한 벌. 포인트·머니 잔액과 두 지갑을 합친 최근 거래 내역을 담는다. {@code moneyPendingAmount} 는 정산 확정 전이라 아직 출금할 수 없는 금액이다.
 */
public record WalletDto(
        long pointAmount,
        long moneyAmount,
        long moneyPendingAmount,
        List<WalletTransactionDto> recentTransactions
) {
}
