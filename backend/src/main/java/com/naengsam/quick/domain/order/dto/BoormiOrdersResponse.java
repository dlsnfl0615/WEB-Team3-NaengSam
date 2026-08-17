package com.naengsam.quick.domain.order.dto;

import java.util.List;

/**
 * 주문 목록의 커서 기반 페이지 조회 응답. {@code nextCursor}는 다음 페이지 요청 시 그대로 넘기면 되는 opaque 값이고,
 * {@code hasNext}가 false면 더 조회할 것이 없다는 뜻이라 nextCursor는 null이다.
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
