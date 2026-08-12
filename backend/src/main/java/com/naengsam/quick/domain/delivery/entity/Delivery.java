package com.naengsam.quick.domain.delivery.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


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

    @Column(name = "last_location_dtm")
    private LocalDateTime lastLocationDtm; // 드리미가 마지막으로 위치를 전송한 시각 — GPS 끊김(무소식) 판정 기준

    @Column(name = "picked_up_dtm")
    private LocalDateTime pickedUpDtm;

    @Column(name = "delivery_start_dtm")
    private LocalDateTime deliveryStartDtm;

    @Column(name = "delivery_end_dtm")
    private LocalDateTime deliveryEndDtm;

    @Column(name = "received_dtm")
    private LocalDateTime receivedDtm;

    @Column(name = "route_path", columnDefinition = "TEXT")
    private String routePath; // 드리미 위치 → 픽업지 카카오 도보 경로 JSON([{latitude, longitude}, ...]) — 픽업 전 추적 지도 폴리라인용

    @Column(name = "estimated_completion_dtm")
    private LocalDateTime estimatedCompletionDtm; // 배송완료예상시간(드리미→픽업지 소요 + 주문 delivery_eta로 산출한 고정 스냅샷)

    // 매칭이 확정된 주문의 배달을 시작한다. PK는 앱에서 생성(BINARY(16))하며 상태는 PICKUP_NORMAL로 시작한다.
    public static Delivery create(UUID orderId, UUID dreamiId, UUID boormiId) {
        Delivery delivery = new Delivery();
        delivery.deliveryId = UUID.randomUUID();
        delivery.orderId = orderId;
        delivery.dreamiId = dreamiId;
        delivery.boormiId = boormiId;
        delivery.deliveryCd = DeliveryCd.PICKUP_NORMAL;
        return delivery;
    }

    // 좌표와 함께 '마지막으로 위치를 받은 시각'을 남긴다. 좌표가 그대로여도 이 시각은 갱신되므로,
    // 드리미가 멈춰 있는 것(정상)과 위치 전송이 끊긴 것(비정상)을 구분할 수 있다.
    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.lastLocationDtm = LocalDateTime.now();
    }

    // 드리미의 첫 위치가 잡힌 뒤 계산한 '드리미→픽업지' 경로와 배송완료예상시간을 한 번에 기록한다(최초 1회만 채운다).
    public void applyPickupRoute(String routePath, LocalDateTime estimatedCompletionDtm) {
        this.routePath = routePath;
        this.estimatedCompletionDtm = estimatedCompletionDtm;
    }

    // 픽업 완료 → 배달중 전이. 픽업/배달 시작 시각을 기록한다.
    public void markDelivering() {
        this.deliveryCd = DeliveryCd.DELIVERING;
        LocalDateTime now = LocalDateTime.now();
        this.pickedUpDtm = now;
        this.deliveryStartDtm = now;
    }

    // 배달 완료 전이. 배달 종료 시각을 기록한다.
    public void markDelivered() {
        this.deliveryCd = DeliveryCd.DELIVERED;
        this.deliveryEndDtm = LocalDateTime.now();
    }

    public void cancelBy(DeliveryCd cancelStatus) {
        if (cancelStatus != DeliveryCd.PICKUP_CANCELLED_BY_BOORMI
                && cancelStatus != DeliveryCd.PICKUP_CANCELLED_BY_DREAMI
                && cancelStatus != DeliveryCd.PICKUP_CANCELLED_BY_ADMIN) {
            throw new IllegalArgumentException("Invalid cancel status: " + cancelStatus);
        }
        this.deliveryCd = cancelStatus;
    }
}
