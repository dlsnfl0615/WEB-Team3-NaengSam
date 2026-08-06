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
 * 외부 PG 를 통한 결제 1건. 포인트 지갑 충전의 근거가 되며 {@code POINT_TX.payment_id} 가 이 행을 가리킨다. PG 연동은 아직 붙지 않았다.
 */
@Entity
@Table(name = "PAYMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_id", columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "payment_amount", nullable = false)
    private Long paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_cd", nullable = false)
    private PaymentCd paymentCd;

    @Column(name = "payment_dtm", nullable = false)
    private LocalDateTime paymentDtm;

    @Column(name = "refund_dtm")
    private LocalDateTime refundDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    public static Payment create(UUID boormiId, Long paymentAmount, PaymentCd paymentCd) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID();
        payment.boormiId = boormiId;
        payment.paymentAmount = paymentAmount;
        payment.paymentCd = paymentCd;
        payment.paymentDtm = LocalDateTime.now();
        return payment;
    }
}
