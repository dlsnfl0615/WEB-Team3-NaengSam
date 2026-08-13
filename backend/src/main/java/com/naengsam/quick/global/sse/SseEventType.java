package com.naengsam.quick.global.sse;

/**
 * SSE 이벤트 종류. 각 도메인이 자신의 이벤트 enum을 이 인터페이스로 구현해
 * {@link com.naengsam.quick.global.notification.NotificationService}로 전송한다. {@link #eventName()}이 SSE event 이름이 된다. 이벤트 이름
 * 충돌을 피하려면 도메인 접두어를 두는 것을 권장한다.
 * <p>
 * SseEventType을 새로 정의할 때는 {@link com.naengsam.quick.global.notification.NotificationPolicy}에 알림 정책을 등록해야 한다.
 * 등록하지 않으면 기본적으로 IN_APP 채널만 사용한다.
 */
public interface SseEventType {

    String eventName();
}
