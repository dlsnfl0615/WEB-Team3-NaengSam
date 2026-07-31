package com.naengsam.quick.global.sse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자별 SSE 연결을 보관하고 전송한다. 사용자 1명당 emitter 1개(단일 연결)이며, 재연결 시 기존 연결을 덮어쓴다. 도메인에 독립적인 순수 인프라이므로 어느 도메인이든
 * {@link SseService}를 통해 재사용한다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    /**
     * SSE 연결 타임아웃. TODO: heartbeat/재연결 정책 확정 후 조정
     */
    private static final long TIMEOUT_MS = 60 * 60 * 1000L;

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(userId, emitter);
        });
        emitter.onError(e -> remove(userId, emitter));

        // 최초 핸드셰이크 이벤트: 프록시 버퍼링을 방지하고 연결 확립을 즉시 알린다.
        sendRaw(userId, emitter, "connected", Map.of());
        log.debug("SSE 연결 등록: userId={}", userId);
        return emitter;
    }

    public void send(UUID userId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("미연결 사용자에게 전송 시도, 무시: userId={}, event={}", userId, eventName);
            return;
        }
        sendRaw(userId, emitter, eventName, payload);
    }

    private void sendRaw(UUID userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception e) {
            // IOException 등: 죽은 클라이언트. 해당 emitter만 정리하고 다른 사용자에는 영향을 주지 않는다.
            log.debug("SSE 전송 실패, emitter 제거: userId={}, event={}", userId, eventName, e);
            emitter.completeWithError(e);
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        // 재연결로 새 emitter가 들어온 경우 최신 것을 지우지 않도록, 값이 일치할 때만 제거한다.
        emitters.remove(userId, emitter);
    }
}
