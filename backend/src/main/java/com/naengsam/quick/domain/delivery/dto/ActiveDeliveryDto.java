package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 페이지의 "진행 중인 배달" 목록 한 행. 강제 취소 대상을 고르는 데 필요한 최소 정보만 담는다.
 */
public record ActiveDeliveryDto(
        UUID orderId,
        UUID boormiId,
        UUID dreamiId,
        DeliveryCd status,
        DeliveryLocationDto currentLocation,
        LocalDateTime lastLocationDtm
) {
    public static ActiveDeliveryDto from(Delivery delivery) {
        DeliveryLocationDto currentLocation = delivery.getCurrentLatitude() == null
                ? null
                : new DeliveryLocationDto(delivery.getCurrentLatitude(), delivery.getCurrentLongitude());

        return new ActiveDeliveryDto(delivery.getOrderId(), delivery.getBoormiId(), delivery.getDreamiId(),
                delivery.getDeliveryCd(), currentLocation, delivery.getLastLocationDtm());
    }
}
