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
 * 로그인 사용자별 SSE 연결을 보관하고 전송한다. 구독마다 서버 내부용 connectionId를 발급하므로, 같은 사용자의 여러 탭과 EventSource
 * 재연결을 독립적으로 유지한다. 도메인에 독립적인 순수 인프라이므로 어느 도메인이든 {@link SseService}를 통해 재사용한다.
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

    private final Map<UUID, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    public SseEmitterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("sse.connections.active", emitters,
                        map -> map.values().stream().mapToInt(Map::size).sum())
                .description("현재 유지 중인 SSE 연결 수")
                .register(meterRegistry);
    }

    public SseEmitter connect(UUID userId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // 마지막 연결 정리와 동시에 실행되어도 새 emitter가 분리된 내부 맵에 등록되지 않도록
        // 사용자 엔트리 생성과 emitter 등록을 하나의 compute 안에서 수행한다.
        emitters.compute(userId, (id, connections) -> {
            Map<String, SseEmitter> current = connections;
            if (current == null) {
                current = new ConcurrentHashMap<>();
            }
            current.put(connectionId, emitter);
            return current;
        });
        meterRegistry.counter("sse.connections.opened").increment();

        emitter.onCompletion(() -> remove(userId, connectionId, emitter, "completion"));
        emitter.onTimeout(() -> {
            // 원인을 timeout 으로 남기기 위해 complete 보다 먼저 제거한다(complete 는 onCompletion 을 태운다).
            remove(userId, connectionId, emitter, "timeout");
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, connectionId, emitter, "error"));

        // 최초 핸드셰이크 이벤트: 프록시 버퍼링을 방지하고 연결 확립을 즉시 알린다.
        sendRaw(userId, connectionId, emitter, "connected", Map.of());
        log.debug("SSE 연결 등록: userId={}, connectionId={}", userId, connectionId);
        return emitter;
    }

    public void send(UUID userId, String eventName, Object payload) {
        Map<String, SseEmitter> connections = emitters.get(userId);
        if (connections == null || connections.isEmpty()) {
            meterRegistry.counter("sse.events.dropped", "reason", "not_connected").increment();
            log.debug("미연결 사용자에게 전송 시도, 무시: userId={}, event={}", userId, eventName);
            return;
        }
        connections.forEach((connectionId, emitter) -> sendRaw(userId, connectionId, emitter, eventName, payload));
    }

    private void sendRaw(UUID userId, String connectionId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            meterRegistry.counter("sse.events.sent", "event", eventName).increment();
        } catch (Exception e) {
            // IOException 등: 죽은 클라이언트. 해당 emitter만 정리하고 같은 사용자의 다른 연결에는 영향을 주지 않는다.
            meterRegistry.counter("sse.events.dropped", "reason", "send_failed").increment();
            log.debug("SSE 전송 실패, emitter 제거: userId={}, connectionId={}, event={}", userId, connectionId, eventName, e);
            // 원인을 send_failed 로 남기기 위해 completeWithError 보다 먼저 제거한다(onError 를 태운다).
            remove(userId, connectionId, emitter, "send_failed");
            emitter.completeWithError(e);
        }
    }

    private void remove(UUID userId, String connectionId, SseEmitter emitter, String reason) {
        // 값이 일치하는 연결만 제거하고, 마지막 연결이 빠지면 사용자 엔트리도 정리한다.
        boolean[] removed = new boolean[1];
        emitters.computeIfPresent(userId, (id, connections) -> {
            removed[0] = connections.remove(connectionId, emitter);
            return connections.isEmpty() ? null : connections;
        });
        if (removed[0]) {
            meterRegistry.counter("sse.connections.closed", "reason", reason).increment();
        }
    }
}
