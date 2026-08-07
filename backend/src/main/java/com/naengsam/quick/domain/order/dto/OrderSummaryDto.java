package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부르미 주문 목록 화면의 카드 한 장에 필요한 요약 정보. 상세 정보는 별도 조회 API에서 다룬다. 드리미 활동 내역에서 재사용
 */
public record OrderSummaryDto(
        UUID orderId,
        String itemName,
        ItemCd itemCd,
        OrderCd orderCd,
        Long deliveryAmount,
        int deliveryEta,
        Long deliveryDistance,
        String originAlias,
        String originAddressLine1,
        String destinationAlias,
        String destinationAddressLine1,
        String imageKey,
        LocalDateTime deliveryRequestDtm
) {
    public static OrderSummaryDto from(Orders order) {
        return new OrderSummaryDto(
                order.getOrderId(),
                order.getItemName(),
                order.getItemCd(),
                order.getOrderCd(),
                order.getDeliveryAmount(),
                order.getDeliveryEta(),
                order.getDeliveryDistance(),
                order.getOriginAlias(),
                order.getOriginAddressLine1(),
                order.getDestinationAlias(),
                order.getDestinationAddressLine1(),
                order.getImageKey(),
                order.getDeliveryRequestDtm()
        );
    }
}
