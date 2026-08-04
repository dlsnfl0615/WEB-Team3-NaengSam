package com.naengsam.quick.domain.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 진행 중인 배달 한 건의 상태·위치를 담는 엔티티. 상태 전이 가드는 DeliveryService가 담당하고, 이 엔티티는 상태·위치·타임스탬프 변경만 책임진다.
 * 주문 단위 직렬화는 DeliveryRepository의 비관적 락 + 트랜잭션으로 보장된다.
 */
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
    private DeliveryCd deliveryCd;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID dreamiId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    @Column(name = "current_latitude", precision = 11, scale = 8)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 11, scale = 8)
    private BigDecimal currentLongitude;

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
