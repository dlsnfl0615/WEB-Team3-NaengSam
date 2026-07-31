package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.service.DeliveryStatus;
import java.util.UUID;

/**
 * 배달 상태 전이/조회의 통합 응답. 기계가 읽는 status(DeliveryCd)로 클라이언트가 분기하고, message는 표시용 설명이다.
 * currentLocation은 드리미의 최신 위치 스냅샷이다(아직 갱신 전이면 null).
 */
public record DeliveryStatusResponseDto(
        UUID orderId,
        DeliveryCd status,
        DeliveryLocationDto currentLocation,
        String message
) {
    public static DeliveryStatusResponseDto from(DeliveryStatus deliveryStatus, String message) {
        DeliveryLocationDto currentLocation = deliveryStatus.currentLatitude() == null
                ? null
                : new DeliveryLocationDto(
                        deliveryStatus.currentLatitude(),
                        deliveryStatus.currentLongitude());

        return new DeliveryStatusResponseDto(
                deliveryStatus.orderId(),
                deliveryStatus.status(),
                currentLocation,
                message);
    }
}
