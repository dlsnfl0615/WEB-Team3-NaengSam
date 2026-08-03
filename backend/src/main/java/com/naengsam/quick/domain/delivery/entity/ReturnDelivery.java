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
@Table(name = "RETURN_DELIVERY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnDelivery {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "return_id", columnDefinition = "BINARY(16)")
    private UUID returnId;

    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;

    @Column(name = "return_start_dtm", nullable = false)
    private LocalDateTime returnStartDtm;

    @Column(name = "return_end_dtm")
    private LocalDateTime returnEndDtm;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_cd")
    private ReturnCd returnCd;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryId;
}
