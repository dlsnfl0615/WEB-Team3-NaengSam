package com.naengsam.quick.domain.delivery.event;

import com.naengsam.quick.global.sse.SseEventType;

/**
 * SSE로 클라이언트에 뿌리는 배달 이벤트 종류. {@link #eventName()}이 SSE event 이름으로 사용된다.
 * 취소 주체(부르미/드리미/관리자)는 payload(DeliveryStatusResponseDto)의 status로 구분한다.
 */
public enum DeliveryEventType implements SseEventType {
    /**
     * 부르미: 드리미 위치 갱신
     */
    DELIVERY_LOCATION,
    /**
     * 부르미: 픽업 완료되어 배달중 상태로 전환
     */
    DELIVERY_DELIVERING,
    /**
     * 부르미/드리미: 배달이 취소됨(주체는 status로 구분)
     */
    DELIVERY_CANCELLED,
    /**
     * 부르미: 배달 완료
     */
    DELIVERY_COMPLETED;

    @Override
    public String eventName() {
        return name().toLowerCase();
    }
}
