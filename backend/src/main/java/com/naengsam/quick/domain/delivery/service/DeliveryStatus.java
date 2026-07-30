package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.util.UUID;

/**
 * 배달 한 건의 상태를 인메모리로 들고 있는 가변 홀더(엔티티/record 아님). 상태 변경은 DeliveryEngine의 단일 스레드에서만 일어나므로,
 * setStatus/setLocation은 패키지 가시성으로 열어 엔진 경유 로직(DeliveryService)만 접근하도록 한다.
 */
public class DeliveryStatus {

    private final UUID orderId;
    private final UUID dreamiId;
    private final UUID boormiId; // order를 통해 주입
    private DeliveryCd status;
    private GeoPoint currentLocation;

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

    public GeoPoint currentLocation() {
        return currentLocation;
    }

    void setStatus(DeliveryCd status) {
        this.status = status;
    }

    void setLocation(GeoPoint location) {
        this.currentLocation = location;
    }
}
