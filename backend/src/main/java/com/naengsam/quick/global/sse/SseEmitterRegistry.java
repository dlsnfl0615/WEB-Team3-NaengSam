package com.naengsam.quick.global.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자별 SSE 연결을 보관하고 전송한다. 사용자 1명당 emitter 1개(단일 연결)이며, 재연결 시 기존 연결을 덮어쓴다. 도메인에 독립적인 순수 인프라이므로 어느 도메인이든
 * {@link SseService}를 통해 재사용한다.
 *
 * <p>연결 수/이벤트 전송량은 Micrometer 로 노출한다(Grafana 관측용). 연결 종료 카운트는 {@link #remove}에서만 세는데, 실제로 맵에서 제거에
 * 성공했을 때만 증가시키므로 complete → onCompletion 처럼 콜백이 연쇄로 불려도 한 번만 집계된다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    /**
     * SSE 연결 타임아웃. TODO: heartbeat/재연결 정책 확정 후 조정
     */
    private static final long TIMEOUT_MS = 60 * 60 * 1000L;

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    public SseEmitterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("sse.connections.active", emitters, Map::size)
                .description("현재 유지 중인 SSE 연결 수")
                .register(meterRegistry);
    }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            // 덮어쓴 연결은 remove 를 거치지 않으므로(값 불일치) 여기서 종료로 집계해야 opened/closed 가 맞는다.
            meterRegistry.counter("sse.connections.closed", "reason", "replaced").increment();
        }
        meterRegistry.counter("sse.connections.opened").increment();

        emitter.onCompletion(() -> remove(userId, emitter, "completion"));
        emitter.onTimeout(() -> {
            // 원인을 timeout 으로 남기기 위해 complete 보다 먼저 제거한다(complete 는 onCompletion 을 태운다).
            remove(userId, emitter, "timeout");
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter, "error"));

        // 최초 핸드셰이크 이벤트: 프록시 버퍼링을 방지하고 연결 확립을 즉시 알린다.
        sendRaw(userId, emitter, "connected", Map.of());
        log.debug("SSE 연결 등록: userId={}", userId);
        return emitter;
    }

    public void send(UUID userId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            meterRegistry.counter("sse.events.dropped", "reason", "not_connected").increment();
            log.debug("미연결 사용자에게 전송 시도, 무시: userId={}, event={}", userId, eventName);
            return;
        }
        sendRaw(userId, emitter, eventName, payload);
    }

    private void sendRaw(UUID userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            meterRegistry.counter("sse.events.sent", "event", eventName).increment();
        } catch (Exception e) {
            // IOException 등: 죽은 클라이언트. 해당 emitter만 정리하고 다른 사용자에는 영향을 주지 않는다.
            meterRegistry.counter("sse.events.dropped", "reason", "send_failed").increment();
            log.debug("SSE 전송 실패, emitter 제거: userId={}, event={}", userId, eventName, e);
            // 원인을 send_failed 로 남기기 위해 completeWithError 보다 먼저 제거한다(onError 를 태운다).
            remove(userId, emitter, "send_failed");
            emitter.completeWithError(e);
        }
    }

    private void remove(UUID userId, SseEmitter emitter, String reason) {
        // 재연결로 새 emitter가 들어온 경우 최신 것을 지우지 않도록, 값이 일치할 때만 제거한다.
        if (emitters.remove(userId, emitter)) {
            meterRegistry.counter("sse.connections.closed", "reason", reason).increment();
        }
    }
}
