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
 * 포인트 지갑의 잔액 변동 이력. {@link PointTx} 1건이 지갑 잔액을 얼마 움직였고({@code amount}) 그 결과 잔액이 얼마가 됐는지({@code balance_after})를 남긴다.
 */
@Entity
@Table(name = "POINT_LEDGERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "point_ledgers_id", columnDefinition = "BINARY(16)")
    private UUID pointLedgersId;

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
    @Column(name = "point_tx_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID pointTxId;

    public static PointLedger create(UUID walletId, UUID pointTxId, Long amount, Long balanceAfter) {
        PointLedger pointLedger = new PointLedger();
        pointLedger.pointLedgersId = UUID.randomUUID();
        pointLedger.walletId = walletId;
        pointLedger.pointTxId = pointTxId;
        pointLedger.amount = amount;
        pointLedger.balanceAfter = balanceAfter;
        pointLedger.createdDtm = LocalDateTime.now();
        return pointLedger;
    }
}
