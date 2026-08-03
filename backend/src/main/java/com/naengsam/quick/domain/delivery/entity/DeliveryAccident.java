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
@Table(name = "DELIVERY_ACCIDENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAccident {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "accident_id", columnDefinition = "BINARY(16)")
    private UUID accidentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryId;

    @Column(name = "accident_type", length = 50)
    private String accidentType;

    @Column(name = "accident_details", columnDefinition = "TEXT")
    private String accidentDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_cd")
    private AccidentCd processingCd;

    @Column(name = "report_dtm", nullable = false)
    private LocalDateTime reportDtm;

    @Column(name = "resolution_dtm")
    private LocalDateTime resolutionDtm;
}
