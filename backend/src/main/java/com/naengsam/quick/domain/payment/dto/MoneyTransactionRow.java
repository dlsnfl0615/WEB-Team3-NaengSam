package com.naengsam.quick.domain.payment.dto;

import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import java.time.LocalDateTime;

/**
 * 머니 원장 한 줄과 그 근거가 된 거래의 유형을 함께 담은 조회 결과. {@code amount} 는 잔액이 얼마나 움직였는지를 나타내는 부호 있는 값이다(정산 +5000, 전환 -5000).
 */
public record MoneyTransactionRow(MoneyTxTypeCd type, long amount, long balanceAfter,
                                  LocalDateTime createdDtm) {
}
