package com.naengsam.quick.domain.dreami.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.service.PickupEtaCalculator;
import com.naengsam.quick.domain.order.dto.NearbyCallOrderDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.util.UUID;

/**
 * 드리미가 주변 콜을 둘러볼 때 보여줄 정보. matching 도메인의 {@link NearbyOrderDto}(위치/거리)와 order 도메인의
 * {@link NearbyCallOrderDto}(품목/주소/예상수익/ETA)를 조합해서 만든다 — matching은 주문의 상세 데이터를 갖고 있지 않기 때문이다.
 */
public record NearbyCallDto(
        UUID orderId,
        GeoPoint location,
        double distanceMeters,
        String itemName,
        ItemCd itemCd,
        OrderCd orderCd,
        Long expectedRevenue,
        int expectedEtaMinutes,
        int pickupEtaMinutes,
        String originAddressLine1,
        String originAddressLine2,
        String destinationAddressLine1,
        String destinationAddressLine2
) {

    public static NearbyCallDto from(NearbyOrderDto nearby, NearbyCallOrderDto order) {
        return new NearbyCallDto(
                nearby.orderId(),
                nearby.location(),
                nearby.distanceMeters(),
                order.itemName(),
                order.itemCd(),
                order.orderCd(),
                order.deliveryAmount(),
                order.deliveryEta(),
                PickupEtaCalculator.minutesFromDistance(nearby.distanceMeters()),
                order.originAddressLine1(),
                order.originAddressLine2(),
                order.destinationAddressLine1(),
                order.destinationAddressLine2()
        );
    }
}
