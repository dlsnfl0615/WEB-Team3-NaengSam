package com.naengsam.quick.domain.dreami.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import java.util.UUID;

/**
 * 드리미가 주변 콜을 둘러볼 때 보여줄 정보. matching 도메인의 {@link NearbyOrderDto}(위치/거리)와 order 도메인의
 * {@link Orders}(품목/주소/예상수익/ETA)를 조합해서 만든다 — matching은 주문의 상세 데이터를 갖고 있지 않기 때문이다.
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
    /** 픽업까지 이동 시간 추정에 쓰는 가정 속도(도보 약 4km/h). */
    private static final double WALK_SPEED_METERS_PER_MINUTE = 4_000.0 / 60;

    public static NearbyCallDto from(NearbyOrderDto nearby, Orders order) {
        return new NearbyCallDto(
                nearby.orderId(),
                nearby.location(),
                nearby.distanceMeters(),
                order.getItemName(),
                order.getItemCd(),
                order.getOrderCd(),
                order.getDeliveryAmount(),
                order.getDeliveryEta(),
                pickupEtaMinutes(nearby.distanceMeters()),
                order.getOriginAddressLine1(),
                order.getOriginAddressLine2(),
                order.getDestinationAddressLine1(),
                order.getDestinationAddressLine2()
        );
    }

    /**
     * 카카오 길찾기 API는 주문 생성 시 1회만 호출한다(deliveryEta). 드리미별 위치는 폴링마다 바뀌므로, 픽업까지
     * 걸리는 시간은 직선거리(distanceMeters)에 도보 속도를 가정해 추정한다 — 호출량 폭증을 피하기 위함이다.
     */
    private static int pickupEtaMinutes(double distanceMeters) {
        return (int) Math.ceil(distanceMeters / WALK_SPEED_METERS_PER_MINUTE);
    }
}
