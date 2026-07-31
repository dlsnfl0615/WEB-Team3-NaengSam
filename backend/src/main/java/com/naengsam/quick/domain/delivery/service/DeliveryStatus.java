package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 배달 한 건의 상태를 인메모리로 들고 있는 가변 홀더(엔티티/record 아님). 상태 변경은 이 객체를 모니터로 삼는 주문 단위 락
 * (DeliveryService의 synchronized) 안에서만 일어나므로, setStatus/setLocation은 패키지 가시성으로 열어 DeliveryService만 접근하도록 한다.
 *
 * <p>훗날 SSE 등 다른 스레드가 락 없이 status/currentLocation을 읽는다면 가시성을 위해 두 필드를 volatile로 두는 것을 고려한다
 * (읽는 쪽이 아직 스텁이라 지금은 불필요).
 */
public class DeliveryStatus {

    private final UUID orderId;
    private final UUID dreamiId;
    private final UUID boormiId; // order를 통해 주입
    private DeliveryCd status;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;

    private DeliveryStatus(UUID orderId, UUID dreamiId, UUID boormiId) {
        this.orderId = orderId;
        this.dreamiId = dreamiId;
        this.boormiId = boormiId;
        this.status = DeliveryCd.PICKUP_NORMAL;
    }

    public static DeliveryStatus create(UUID orderId, UUID dreamiId, UUID boormiId) {
        return new DeliveryStatus(orderId, dreamiId, boormiId);
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID dreamiId() {
        return dreamiId;
    }

    public UUID boormiId() {
        return boormiId;
    }

    public DeliveryCd status() {
        return status;
    }

    public BigDecimal currentLatitude() {
        return currentLatitude;
    }

    public BigDecimal currentLongitude() {
        return currentLongitude;
    }

    void setStatus(DeliveryCd status) {
        this.status = status;
    }

    void setLocation(BigDecimal latitude, BigDecimal longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
    }
}
