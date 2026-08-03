package com.naengsam.quick.domain.delivery.entity;

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
@Table(name = "COMPENSATION_CLAIM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompensationClaim {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "claim_id", columnDefinition = "BINARY(16)")
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_cd", nullable = false)
    private CompensationCd requestCd;

    @Column(name = "claim_amount", nullable = false)
    private Long claimAmount;

    @Column(name = "compensation_amount", nullable = false)
    private Long compensationAmount;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "audit_dtm")
    private LocalDateTime auditDtm;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "accident_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID accidentId;
}
