package com.naengsam.quick.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "MONEY_TX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyTx {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_tx_id", columnDefinition = "BINARY(16)")
    private UUID moneyTxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MoneyTxStatus status;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "created_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdDtm;

    @Column(name = "updated_dtm")
    private LocalDateTime updatedDtm;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private MoneyTxType type;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;
}
