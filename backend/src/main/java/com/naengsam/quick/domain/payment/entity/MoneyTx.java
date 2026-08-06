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

/**
 * 머니 지갑의 거래 1건. 배달 완료에 따른 정산(SETTLEMENT), 정산 취소(REVERSAL), 보상 조정(CLAIM_ADJUSTMENT), 포인트로의 전환(EXCHANGE_OUT)을 기록한다. 정산
 * 확정 전에는 PENDING 상태로 남는다.
 */
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
    @Column(name = "type", nullable = false)
    private MoneyTxTypeCd type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MoneyTxStatusCd status;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @Column(name = "updated_dtm")
    private LocalDateTime updatedDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;

    /**
     * 머니 거래를 생성한다. 상태는 항상 PENDING 으로 시작하며, 정산 확정/거절 전이는 이후 별도로 처리한다.
     */
    public static MoneyTx create(UUID walletId, MoneyTxTypeCd type, Long amount, UUID orderId) {
        MoneyTx moneyTx = new MoneyTx();
        moneyTx.moneyTxId = UUID.randomUUID();
        moneyTx.walletId = walletId;
        moneyTx.type = type;
        moneyTx.status = MoneyTxStatusCd.PENDING;
        moneyTx.amount = amount;
        moneyTx.orderId = orderId;
        moneyTx.createdDtm = LocalDateTime.now();
        return moneyTx;
    }
}
