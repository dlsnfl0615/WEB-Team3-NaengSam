package com.naengsam.quick.domain.order.dto;

import java.util.List;

/**
 * 주문 목록 전체 조회 응답.
 */
public record BoormiOrdersResponse(
        List<OrderSummaryDto> orders
) {
    public static BoormiOrdersResponse of(List<OrderSummaryDto> orders) {
        return new BoormiOrdersResponse(orders);
    }
}
