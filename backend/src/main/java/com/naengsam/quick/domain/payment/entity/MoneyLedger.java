package com.naengsam.quick.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 머니 지갑의 잔액 변동 이력. {@link MoneyTx} 1건이 지갑 잔액을 얼마 움직였고({@code amount}) 그 결과 잔액이 얼마가 됐는지({@code balance_after})를 남긴다.
 */
@Entity
@Table(name = "MONEY_LEDGERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyLedger {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_ledgers_id", columnDefinition = "BINARY(16)")
    private UUID moneyLedgersId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_tx_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID moneyTxId;

    public static MoneyLedger create(UUID walletId, UUID moneyTxId, Long amount, Long balanceAfter) {
        MoneyLedger moneyLedger = new MoneyLedger();
        moneyLedger.moneyLedgersId = UUID.randomUUID();
        moneyLedger.walletId = walletId;
        moneyLedger.moneyTxId = moneyTxId;
        moneyLedger.amount = amount;
        moneyLedger.balanceAfter = balanceAfter;
        moneyLedger.createdDtm = LocalDateTime.now();
        return moneyLedger;
    }
}
