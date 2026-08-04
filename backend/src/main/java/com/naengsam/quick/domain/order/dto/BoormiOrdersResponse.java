package com.naengsam.quick.domain.order.dto;

import java.util.List;

/**
 * 커서 기반 주문 목록 조회 응답. {@code nextCursor} 는 다음 페이지 조회에 그대로 넘기며, 다음 페이지가 없으면 {@code hasNext=false} 이고 null 이다.
 */
public record BoormiOrdersResponse(
        List<OrderSummaryDto> orders,
        String nextCursor,
        boolean hasNext
) {
    public static BoormiOrdersResponse of(List<OrderSummaryDto> orders, String nextCursor, boolean hasNext) {
        return new BoormiOrdersResponse(orders, nextCursor, hasNext);
    }
}
