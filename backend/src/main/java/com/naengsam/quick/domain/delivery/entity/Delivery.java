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
@Table(name = "DELIVERY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_id", columnDefinition = "BINARY(16)")
    private UUID deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_cd", nullable = false)
    private DeliveryStatusCd deliveryStatusCd;

    @Column(name = "picked_up_dtm")
    private LocalDateTime pickedUpDtm;

    @Column(name = "delivery_start_dtm")
    private LocalDateTime deliveryStartDtm;

    @Column(name = "delivery_end_dtm")
    private LocalDateTime deliveryEndDtm;

    @Column(name = "received_dtm")
    private LocalDateTime receivedDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;
}
