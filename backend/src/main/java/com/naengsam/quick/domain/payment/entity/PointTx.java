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
 * 포인트 지갑의 거래 1건. 충전(CHARGE)은 {@code payment_id} 를, 배달 콜 결제(PAYMENT)는 {@code order_id} 를 근거로 갖고, 머니→포인트
 * 전환(EXCHANGE_IN)은 둘 다 비어 있다.
 * <p>
 * 이 행은 거래 건의 <b>현재 상태</b>를 나타낸다. 환불은 새 행을 만들지 않고 원본 결제 행의 {@code status} 를 REFUNDED_FULL 로 전이시키므로, 4000원을 결제한 뒤
 * 환불하면 이 테이블에는 net 0 인 행 하나만 남는다. 개별 잔액 변동(-4000, +4000)은 {@link PointLedger} 가 로그로 남긴다.
 */
@Entity
@Table(name = "POINT_TX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTx {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "point_tx_id", columnDefinition = "BINARY(16)")
    private UUID pointTxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PointTxTypeCd type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PointTxStatusCd status;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @Column(name = "updated_dtm")
    private LocalDateTime updatedDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_id", columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID walletId;

    /**
     * 포인트 거래를 생성한다. 상태는 항상 PAID 로 시작하며, 환불 전이는 이후 별도로 처리한다. {@code paymentId} / {@code orderId} 는 거래 유형에 해당하지 않으면
     * null 이다.
     */
    public static PointTx create(UUID walletId, PointTxTypeCd type, Long amount,
            UUID paymentId, UUID orderId) {
        PointTx pointTx = new PointTx();
        pointTx.pointTxId = UUID.randomUUID();
        pointTx.walletId = walletId;
        pointTx.type = type;
        pointTx.status = PointTxStatusCd.PAID;
        pointTx.amount = amount;
        pointTx.paymentId = paymentId;
        pointTx.orderId = orderId;
        pointTx.createdDtm = LocalDateTime.now();
        return pointTx;
    }

    /**
     * 이 거래를 전액 환불 상태로 전이한다. 이미 환불된 거래면 아무것도 하지 않아 같은 요청이 두 번 들어와도 결과가 같다.
     *
     * @return 이번 호출로 실제 전이가 일어났으면 true
     */
    public boolean markRefundedFull() {
        if (this.status == PointTxStatusCd.REFUNDED_FULL) {
            return false;
        }
        this.status = PointTxStatusCd.REFUNDED_FULL;
        this.updatedDtm = LocalDateTime.now();
        return true;
    }
}
