package com.naengsam.quick.global.sse;

/**
 * SSE 이벤트 종류. 각 도메인이 자신의 이벤트 enum을 이 인터페이스로 구현해 {@link SseService}로 전송한다. {@link #eventName()}이 SSE event 이름이 된다.
 * 이벤트 이름 충돌을 피하려면 도메인 접두어를 두는 것을 권장한다.
 */
public interface SseEventType {

    String eventName();
}
