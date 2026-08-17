package com.naengsam.quick.domain.order.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * 활동 내역 목록의 커서. 마지막으로 본 행의 정렬 키(delivery_request_dtm, order_id)를 그대로 담아, 다음 페이지 조회 시
 * "이 값보다 이전 것들"의 기준으로 쓴다. 클라이언트에는 opaque한 문자열로만 노출한다.
 */
public record OrderCursor(LocalDateTime deliveryRequestDtm, UUID orderId) {

    private static final OrderCursor FIRST_PAGE = new OrderCursor(null, null);

    public static OrderCursor of(OrderSummaryDto lastItem) {
        return new OrderCursor(lastItem.deliveryRequestDtm(), lastItem.orderId());
    }

    /**
     * 커서 문자열을 복원한다. null/공백(첫 페이지)은 정상 케이스로 보고 빈 커서를 반환하고,
     * 형식이 깨진 값만 실패로 취급한다.
     */
    public static Optional<OrderCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.of(FIRST_PAGE);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return Optional.of(new OrderCursor(LocalDateTime.parse(parts[0]), UUID.fromString(parts[1])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public String encode() {
        String raw = deliveryRequestDtm + "|" + orderId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
