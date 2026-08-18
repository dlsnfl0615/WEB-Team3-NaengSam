package com.naengsam.quick.global.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link com.naengsam.quick.global.session.ActiveSession}이 최대 하나만 보유하는 서버 SSE emitter 슬롯.
 * connectionId는 새 구독이 원자적으로 옛 연결을 교체할 때, 늦게 도착한 이전 연결의 completion/error/heartbeat
 * 콜백이나 disconnect 요청이 이미 교체된 새 연결을 잘못 제거하지 않도록 식별하는 데 쓴다.
 */
public record SseConnection(String connectionId, SseEmitter emitter) {
}
