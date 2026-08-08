package com.naengsam.quick.domain.dreami.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.dreami.entity.DreamiReview;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 드리미 활동 내역 목록 화면의 카드 한 장에 필요한 정보. 주문(Orders) 정보에 배달(Delivery) 상태·완료 시각,
 * 드리미 평점(DreamiReview)을 함께 담는다. delivery/review는 아직 없을 수 있어 null일 수 있다.
 */
public record DreamiDeliveryHistoryDto(
        UUID orderId,
        String itemName,
        ItemCd itemCd,
        OrderCd orderCd,
        DeliveryCd deliveryCd,
        Long deliveryAmount,
        String originAlias,
        String originAddressLine1,
        String originAddressLine2,
        String destinationAlias,
        String destinationAddressLine1,
        String destinationAddressLine2,
        LocalDateTime deliveryEndDtm,
        Integer rating
) {
    public static DreamiDeliveryHistoryDto of(Orders order, Delivery delivery, DreamiReview review) {
        return new DreamiDeliveryHistoryDto(
                order.getOrderId(),
                order.getItemName(),
                order.getItemCd(),
                order.getOrderCd(),
                delivery == null ? null : delivery.getDeliveryCd(), // 메서드 호출하기 전에 null인지 확인
                order.getDeliveryAmount(),
                order.getOriginAlias(),
                order.getOriginAddressLine1(),
                order.getOriginAddressLine2(),
                order.getDestinationAlias(),
                order.getDestinationAddressLine1(),
                order.getDestinationAddressLine2(),
                delivery == null ? null : delivery.getDeliveryEndDtm(),
                review == null ? null : review.getScore()
        );
    }
}
