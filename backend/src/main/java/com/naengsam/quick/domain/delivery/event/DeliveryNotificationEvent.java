package com.naengsam.quick.domain.delivery.event;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;

import java.util.UUID;

/** 배달 SSE 알림 발행용 이벤트. 트랜잭션 커밋 후(AFTER_COMMIT)에만 실제 전송된다. */
public record DeliveryNotificationEvent(UUID userId, DeliveryEventType eventType, DeliveryStatusResponseDto payload) {
}
