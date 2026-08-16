package com.naengsam.quick.global.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자별 SSE 연결을 보관하고 전송한다. 구독마다 서버 내부용 connectionId를 발급하므로, 같은 사용자의 여러 탭과 EventSource
 * 재연결을 독립적으로 유지한다. 도메인에 독립적인 순수 인프라이며,
 * {@link com.naengsam.quick.global.notification.NotificationService}의 IN_APP 채널이 {@link SseService}를 통해 사용한다.
 *
 * <p>연결 수/이벤트 전송량은 Micrometer 로 노출한다(Grafana 관측용). 연결 종료 카운트는 {@link #remove}와
 * {@link #disconnectAll}에서만 세는데, 둘 다 emitter를 완료시키기 전에 맵에서 먼저 제거하므로 complete →
 * onCompletion 처럼 콜백이 연쇄로 불려도 이미 제거된 뒤라 한 번만 집계된다.
 *
 * <p>{@link #sendHeartbeats}는 프록시/브라우저가 말없이 끊어버린 유령 연결을 정리한다. 실패한 connection 하나만
 * 제거하며, 같은 사용자의 다른 탭까지 끊지 않는다(그건 {@link #disconnectAll}의 역할).
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<UUID, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final SseProperties sseProperties;

    public SseEmitterRegistry(MeterRegistry meterRegistry, SseProperties sseProperties) {
        this.meterRegistry = meterRegistry;
        this.sseProperties = sseProperties;
        Gauge.builder("sse.connections.active", emitters,
                        map -> map.values().stream().mapToInt(Map::size).sum())
                .description("현재 유지 중인 SSE 연결 수")
                .register(meterRegistry);
    }

    /**
     * 사용자의 새 SSE 연결을 등록하고 emitter를 돌려준다. 사용자당 연결 수가
     * {@link SseProperties#maxConnectionsPerUser()} 상한에 도달했으면 새 연결을 등록하지 않고 {@code null}을 반환한다
     * (기존 연결은 그대로 유지). 컨트롤러는 이 {@code null}을 204로 변환해 native EventSource의 자동 재연결을 멈춘다.
     */
    public SseEmitter connect(UUID userId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(sseProperties.connectionTimeout().toMillis());

        // 마지막 연결 정리와 동시에 실행되어도 새 emitter가 분리된 내부 맵에 등록되지 않도록,
        // 그리고 동시 요청에서도 상한을 정확히 지키도록 한도 검사와 등록을 하나의 compute 안에서 원자적으로 수행한다.
        boolean[] registered = {false};
        emitters.compute(userId, (id, connections) -> {
            Map<String, SseEmitter> current = connections;
            if (current == null) {
                current = new ConcurrentHashMap<>();
            }
            if (current.size() >= sseProperties.maxConnectionsPerUser()) {
                // 한도 초과: 새 연결을 등록하지 않는다. 기존 연결이 있으면 그대로 두고, 빈 엔트리는 남기지 않는다.
                return current.isEmpty() ? null : current;
            }
            current.put(connectionId, emitter);
            registered[0] = true;
            return current;
        });

        if (!registered[0]) {
            meterRegistry.counter("sse.connections.rejected").increment();
            log.debug("SSE 연결 한도 초과로 거부: userId={}, limit={}", userId, sseProperties.maxConnectionsPerUser());
            return null;
        }
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

    /** 현재 사용자에게 등록된 SSE 연결이 하나라도 있는지 락 없이 조회한다. */
    public boolean isConnected(UUID userId) {
        Map<String, SseEmitter> connections = emitters.get(userId);
        return connections != null && !connections.isEmpty();
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

    public void disconnectAll(UUID userId, SseCloseReason reason) {
        // 콜백에 의한 remove()가 중복 집계되지 않도록, 맵에서 먼저 사용자 엔트리를 통째로 제거한 뒤 emitter를 종료한다.
        Map<String, SseEmitter> connections = emitters.remove(userId);
        if (connections == null) {
            return;
        }

        String metricReason = reason.name().toLowerCase();
        connections.values().forEach(emitter -> {
            meterRegistry.counter("sse.connections.closed", "reason", metricReason).increment();
            emitter.complete();
        });
        log.debug("사용자 SSE 연결 전체 종료: userId={}, reason={}, count={}", userId, reason, connections.size());
    }

    /**
     * 모든 연결에 heartbeat 주석을 보내 프록시/브라우저가 타임아웃으로 끊지 않게 하고, 이미 죽은 연결을 찾아 정리한다.
     * 데이터 없는 SSE 주석(":heartbeat")이라 클라이언트의 EventSource에는 노출되지 않는다.
     */
    // 프로퍼티 플레이스홀더로 직접 주기를 읽는다. SpEL로 빈(sseProperties)을 이름으로 참조하면
    // @EnableConfigurationProperties가 붙이는 실제 빈 이름(sse-<FQCN>)과 달라 부팅 시 해석에 실패한다.
    @Scheduled(fixedRateString = "${sse.heartbeat-interval}")
    public void sendHeartbeats() {
        emitters.forEach((userId, connections) ->
                connections.forEach((connectionId, emitter) -> sendHeartbeat(userId, connectionId, emitter)));
    }

    private void sendHeartbeat(UUID userId, String connectionId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception e) {
            // 실패한 connection만 제거한다. 같은 사용자의 다른 탭까지 끊으면 안 되므로 disconnectAll을 쓰지 않는다.
            log.debug("SSE heartbeat 실패, emitter 제거: userId={}, connectionId={}", userId, connectionId, e);
            remove(userId, connectionId, emitter, "heartbeat_failed");
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
