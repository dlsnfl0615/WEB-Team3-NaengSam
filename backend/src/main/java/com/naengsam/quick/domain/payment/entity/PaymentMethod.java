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
 * 회원이 등록해 둔 결제수단. PG 가 발급한 빌링키를 보관해 재결제에 사용한다. 해지는 {@code deleted_dtm} 으로 표시하는 소프트 삭제다.
 */
@Entity
@Table(name = "PAYMENT_METHOD")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_method_id", columnDefinition = "BINARY(16)")
    private UUID paymentMethodId;

    @Column(name = "billing_key", nullable = false, unique = true)
    private String billingKey;

    @Column(name = "pg_name", nullable = false)
    private String pgName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_cd", nullable = false)
    private PaymentCd paymentMethodCd;

    @Column(name = "masking_no", length = 20, nullable = false)
    private String maskingNo;

    @Column(name = "is_default_payment", nullable = false)
    private boolean isDefaultPayment;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @Column(name = "updated_dtm")
    private LocalDateTime updatedDtm;

    @Column(name = "deleted_dtm")
    private LocalDateTime deletedDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    public static PaymentMethod create(UUID boormiId, String billingKey, String pgName,
            PaymentCd paymentMethodCd, String maskingNo, boolean isDefaultPayment) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.paymentMethodId = UUID.randomUUID();
        paymentMethod.boormiId = boormiId;
        paymentMethod.billingKey = billingKey;
        paymentMethod.pgName = pgName;
        paymentMethod.paymentMethodCd = paymentMethodCd;
        paymentMethod.maskingNo = maskingNo;
        paymentMethod.isDefaultPayment = isDefaultPayment;
        paymentMethod.createdDtm = LocalDateTime.now();
        return paymentMethod;
    }
}
