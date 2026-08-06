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

@Entity
@Table(name = "MONEY_LEDGERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyLedgers {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_ledgers_id", columnDefinition = "BINARY(16)")
    private UUID moneyLedgersId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "created_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_tx_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID moneyTxId;
}
