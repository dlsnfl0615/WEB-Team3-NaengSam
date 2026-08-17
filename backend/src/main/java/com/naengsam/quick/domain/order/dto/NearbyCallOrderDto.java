package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.util.UUID;

/**
 * 주변 콜 목록 한 줄에 필요한 주문 컬럼만 담은 조회 결과. 목록을 그릴 때 주문마다 PK 조회를 반복하지 않으려고 id 목록으로 한 번에 읽기 위한 프로젝션이며, 엔티티 전체를 싣지 않는다.
 */
public record NearbyCallOrderDto(
        UUID orderId,
        String itemName,
        ItemCd itemCd,
        OrderCd orderCd,
        Long deliveryAmount,
        int deliveryEta,
        String originAddressLine1,
        String originAddressLine2,
        String destinationAddressLine1,
        String destinationAddressLine2
) {
}
