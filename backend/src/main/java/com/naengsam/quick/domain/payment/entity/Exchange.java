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
 * 머니 → 포인트 전환 1건. 나가는 쪽 {@link MoneyTx}(EXCHANGE_OUT)와 들어오는 쪽 {@link PointTx}(EXCHANGE_IN)를 짝지어 기록한다.
 */
@Entity
@Table(name = "EXCHANGES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exchange {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "exchanges_id", columnDefinition = "BINARY(16)")
    private UUID exchangesId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "point_tx_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID pointTxId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "money_tx_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID moneyTxId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;

    public static Exchange create(UUID walletId, UUID pointTxId, UUID moneyTxId, Long amount) {
        Exchange exchange = new Exchange();
        exchange.exchangesId = UUID.randomUUID();
        exchange.walletId = walletId;
        exchange.pointTxId = pointTxId;
        exchange.moneyTxId = moneyTxId;
        exchange.amount = amount;
        exchange.createdDtm = LocalDateTime.now();
        return exchange;
    }
}
