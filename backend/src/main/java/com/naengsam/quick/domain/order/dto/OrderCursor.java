package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

/**
 * 주문 목록 커서. 정렬 키(delivery_request_dtm, order_id)를 그대로 담아 Base64 로 인코딩한 불투명 문자열로 주고받는다. 시각은 epochMilli 로 직렬화해 타임존/포맷 흔들림을 없앤다.
 */
public record OrderCursor(LocalDateTime dtm, UUID orderId) {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public String encode() {
        long epochMilli = dtm.atZone(ZONE).toInstant().toEpochMilli();
        String raw = epochMilli + "|" + orderId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static OrderCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("missing separator");
            }
            long epochMilli = Long.parseLong(raw.substring(0, separator));
            LocalDateTime dtm = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE);
            UUID orderId = UUID.fromString(raw.substring(separator + 1));
            return new OrderCursor(dtm, orderId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_CURSOR);
        }
    }
}
