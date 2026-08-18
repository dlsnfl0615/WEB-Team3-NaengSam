package com.naengsam.quick.global.sse;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.ActiveSessionRegistry;
import com.naengsam.quick.global.session.ActiveSessionRegistry.ActiveSessionSnapshot;
import com.naengsam.quick.global.session.ActiveSessionRegistry.SseReplacement;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 계정당 최대 하나인 SSE 연결의 생명주기(수립·교체·종료)와 전송을 관리한다. 연결 소유권의 단일 진실 공급원은
 * {@link ActiveSessionRegistry}이고, 이 클래스는 그 위에서 emitter 콜백 등록과 실제 전송만 담당하는 순수 인프라다.
 * {@link com.naengsam.quick.global.notification.NotificationService}의 IN_APP 채널이 {@link SseService}를 통해
 * 사용한다.
 *
 * <p>연결 수/이벤트 전송량은 Micrometer로 노출한다(Grafana 관측용).
 *
 * <p>{@link #sendHeartbeats}는 프록시/브라우저가 말없이 끊어버린 유령 연결을 정리한다. 사용자가 모든 탭을 닫고
 * 돌아오지 않는 경우의 최종 회수 수단일 뿐이며, 재접속은 heartbeat를 기다리지 않고 {@link #connect}의 즉시 교체로
 * 처리된다.
 */
@Slf4j
@Component
public class SseConnectionManager {

    private final ActiveSessionRegistry activeSessionRegistry;
    private final MeterRegistry meterRegistry;
    private final SseProperties sseProperties;

    public SseConnectionManager(ActiveSessionRegistry activeSessionRegistry, MeterRegistry meterRegistry,
            SseProperties sseProperties) {
        this.activeSessionRegistry = activeSessionRegistry;
        this.meterRegistry = meterRegistry;
        this.sseProperties = sseProperties;
        Gauge.builder("sse.connections.active", activeSessionRegistry,
                        registry -> registry.snapshotSseConnections().size())
                .description("현재 유지 중인 SSE 연결 수")
                .register(meterRegistry);
    }

    /**
     * 사용자의 새 SSE 연결을 수립한다. sessionId가 더 이상 계정의 현재 세션이 아니면(인터셉터 통과 직후 다른 곳에서
     * 로그인한 경합) 새 emitter를 등록하지 않고 즉시 종료한 뒤 인증 오류로 처리한다. 그렇지 않으면 기존 연결이 있어도
     * 거부하지 않고 즉시 교체하며, 이전 emitter는 registry 연산이 끝난 뒤에 종료한다({@code complete()}가 태우는
     * completion 콜백이 같은 연산에 재진입하지 않도록).
     */
    public SseEmitter connect(UUID userId, String sessionId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(sseProperties.connectionTimeout().toMillis());
        SseConnection connection = new SseConnection(connectionId, emitter);

        SseReplacement replacement = activeSessionRegistry.replaceSseIfCurrent(userId, sessionId, connection);
        if (!replacement.registered()) {
            log.debug("SSE 구독 시점에 세션이 이미 교체됨, 등록 거부: userId={}", userId);
            emitter.complete();
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        emitter.onCompletion(() -> remove(userId, sessionId, connectionId, "completion"));
        emitter.onTimeout(() -> {
            // 원인을 timeout으로 남기기 위해 complete보다 먼저 제거한다(complete는 onCompletion을 태운다).
            remove(userId, sessionId, connectionId, "timeout");
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, sessionId, connectionId, "error"));

        if (replacement.previous() != null) {
            close(replacement.previous(), SseCloseReason.REPLACED);
        }
        meterRegistry.counter("sse.connections.opened").increment();

        sendRaw(userId, sessionId, connectionId, emitter, "connected", Map.of("connectionId", connectionId));
        log.debug("SSE 연결 등록: userId={}, connectionId={}", userId, connectionId);
        return emitter;
    }

    /**
     * 클라이언트의 명시적 disconnect 요청. userId·sessionId·connectionId가 모두 지금 이 계정의 현재 연결과 일치할
     * 때만 제거한다. 이전 페이지의 늦은 요청(예: 새로고침 전 sendBeacon)은 connectionId가 이미 교체된 새 연결과
     * 달라 idempotent no-op이 된다.
     */
    public void disconnect(UUID userId, String sessionId, String connectionId) {
        activeSessionRegistry.removeSseIfCurrent(userId, sessionId, connectionId)
                .ifPresent(connection -> close(connection, SseCloseReason.CLIENT_DISCONNECT));
    }

    /**
     * 계정 현재 연결에만 전송한다. 전송 시작 시 connection을 스냅샷으로 가져오므로, 전송 도중 다른 탭이 연결을
     * 교체해도 이 전송은 스냅샷 시점의 연결로 끝까지 진행된다. 전송 실패 시에는 그 스냅샷이 여전히 현재 연결일 때만
     * registry에서 제거한다 — 이미 새 연결로 교체됐다면 그 사이 실패는 새 연결과 무관하다.
     */
    public void send(UUID userId, String eventName, Object payload) {
        Optional<ActiveSessionSnapshot> snapshot = activeSessionRegistry.findSseSnapshot(userId);
        if (snapshot.isEmpty()) {
            meterRegistry.counter("sse.events.dropped", "reason", "not_connected").increment();
            log.debug("미연결 사용자에게 전송 시도, 무시: userId={}, event={}", userId, eventName);
            return;
        }

        ActiveSessionSnapshot current = snapshot.get();
        sendRaw(current.userId(), current.sessionId(), current.sseConnection().connectionId(),
                current.sseConnection().emitter(), eventName, payload);
    }

    /** 현재 사용자에게 SSE 연결이 있는지 락 없이 조회한다. */
    public boolean isConnected(UUID userId) {
        return activeSessionRegistry.isSseConnected(userId);
    }

    /**
     * 이미 registry에서 분리된(또는 분리와 함께 넘겨받은) {@link SseConnection}을 종료한다. 로그아웃·다른 곳에서
     * 로그인·세션 만료처럼 registry 쪽 제거가 먼저 끝난 뒤 호출자가 들고 있는 연결을 정리할 때 쓴다.
     */
    public void close(SseConnection connection, SseCloseReason reason) {
        meterRegistry.counter("sse.connections.closed", "reason", reason.name().toLowerCase()).increment();
        connection.emitter().complete();
    }

    private void sendRaw(UUID userId, String sessionId, String connectionId, SseEmitter emitter, String eventName,
            Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            meterRegistry.counter("sse.events.sent", "event", eventName).increment();
        } catch (Exception e) {
            // IOException 등: 죽은 클라이언트. 원인을 send_failed로 남기기 위해 completeWithError보다 먼저
            // registry에서 제거한다(completeWithError는 onError를 태운다).
            meterRegistry.counter("sse.events.dropped", "reason", "send_failed").increment();
            log.debug("SSE 전송 실패, 연결 제거: userId={}, connectionId={}, event={}", userId, connectionId, eventName, e);
            remove(userId, sessionId, connectionId, "send_failed");
            emitter.completeWithError(e);
        }
    }

    /**
     * 모든 연결에 heartbeat 주석을 보내 프록시/브라우저가 타임아웃으로 끊지 않게 하고, 이미 죽은 연결을 찾아
     * 정리한다. 데이터 없는 SSE 주석(":heartbeat")이라 클라이언트의 EventSource에는 노출되지 않는다.
     * {@link ActiveSessionRegistry#snapshotSseConnections}의 불변 스냅샷을 순회하므로, heartbeat 도중 다른 요청이
     * 연결을 교체해도 이 순회 자체에는 영향이 없다(교체된 연결의 제거는 아래 {@link #remove}가 identity로 걸러낸다).
     */
    // 프로퍼티 플레이스홀더로 직접 주기를 읽는다. SpEL로 빈(sseProperties)을 이름으로 참조하면
    // @EnableConfigurationProperties가 붙이는 실제 빈 이름(sse-<FQCN>)과 달라 부팅 시 해석에 실패한다.
    @Scheduled(fixedRateString = "${sse.heartbeat-interval}")
    public void sendHeartbeats() {
        activeSessionRegistry.snapshotSseConnections()
                .forEach(snapshot -> sendHeartbeat(snapshot.userId(), snapshot.sessionId(), snapshot.sseConnection()));
    }

    private void sendHeartbeat(UUID userId, String sessionId, SseConnection connection) {
        try {
            connection.emitter().send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception e) {
            log.debug("SSE heartbeat 실패, 연결 제거: userId={}, connectionId={}", userId, connection.connectionId(), e);
            remove(userId, sessionId, connection.connectionId(), "heartbeat_failed");
            connection.emitter().completeWithError(e);
        }
    }

    /**
     * userId·sessionId·connectionId가 모두 일치할 때만 registry에서 제거하고, 실제로 제거됐을 때만 closed
     * 메트릭을 집계한다. 이미 다른 연결로 교체된 뒤 도착한 콜백은 조용히 no-op이 되어 새 연결을 건드리지 않는다.
     */
    private void remove(UUID userId, String sessionId, String connectionId, String reason) {
        activeSessionRegistry.removeSseIfCurrent(userId, sessionId, connectionId)
                .ifPresent(removed -> meterRegistry.counter("sse.connections.closed", "reason", reason).increment());
    }
}
