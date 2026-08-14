package com.naengsam.quick.domain.delivery.event;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;

import java.util.UUID;

/**
 * 배달 SSE 알림 발행용 이벤트. 트랜잭션 커밋 후(AFTER_COMMIT)에만 실제 전송된다.
 *
 * @param pushSubject 웹푸시 제목 앞에 붙일 대상(물품명). 잠금화면에서 어떤 배달인지 알아보게 하는 용도라
 *                    <b>이미 성사된 배달의 진행 알림에만</b> 채운다. 그 외에는 null.
 */
public record DeliveryNotificationEvent(
        UUID userId,
        DeliveryEventType eventType,
        DeliveryStatusResponseDto payload,
        String pushSubject) {

    /** 물품명을 싣지 않는 기본 알림(매칭·취소 등). */
    public DeliveryNotificationEvent(UUID userId, DeliveryEventType eventType, DeliveryStatusResponseDto payload) {
        this(userId, eventType, payload, null);
    }
}
