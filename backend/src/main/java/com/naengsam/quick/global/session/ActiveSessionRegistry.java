package com.naengsam.quick.global.session;

import com.naengsam.quick.global.admin.InMemoryStateProbe;
import com.naengsam.quick.global.admin.InMemoryStructureDto;
import com.naengsam.quick.global.sse.SseConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사용자당 활성 세션을 하나만, 활성 세션당 SSE 연결을 하나만 유지하는 단일 진실 공급원. 새로 로그인하면 {@link #replace}가 이전
 * 활성 세션(과 그 세션이 들고 있던 SSE 연결)을 밀어내고 반환하므로, 호출자는 반환된 세션을 이용해 이전 SSE 연결 종료와 이전 세션 무효화를
 * 진행한다.
 */
@Slf4j
@Component
public class ActiveSessionRegistry implements InMemoryStateProbe {

    private final Map<UUID, ActiveSession> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> usersBySessionId = new ConcurrentHashMap<>();

    /**
     * 사용자의 활성 세션을 SSE 슬롯이 비어 있는 새 세션으로 교체하고 이전 세션을 반환한다(없으면 null). 동시에 여러 로그인 요청이
     * 들어와도 사용자당 맵에 순서대로 반영되는 마지막 {@code put}만 최종 상태로 남으므로, 뒤에 반영된 호출이 앞선 호출의 세션을
     * previous로 받아 정리하게 되어 결과적으로 마지막 세션 하나만 남는다.
     */
    public ActiveSession replace(UUID userId, LoginSession newSession) {
        ActiveSession newEntry = ActiveSession.create(newSession);
        usersBySessionId.put(newEntry.sessionId(), userId);

        ActiveSession previous = sessionsByUser.put(userId, newEntry);
        if (previous != null && !previous.sessionId().equals(newEntry.sessionId())) {
            usersBySessionId.remove(previous.sessionId(), userId);
        }
        log.debug("사용자 활성 세션 교체: userId={}, 이전 세션 존재={}", userId, previous != null);
        return previous;
    }

    /**
     * 주어진 sessionId가 여전히 해당 사용자의 활성 세션일 때만 제거한다(이미 다른 로그인으로 교체됐으면 no-op). 제거된 세션이 SSE
     * 연결을 들고 있었다면 호출자가 {@link RemovedSession#activeSession()}에서 꺼내 종료해야 한다.
     */
    public Optional<RemovedSession> removeIfCurrent(String sessionId) {
        UUID userId = usersBySessionId.get(sessionId);
        if (userId == null) {
            return Optional.empty();
        }

        ActiveSession[] removed = new ActiveSession[1];
        sessionsByUser.computeIfPresent(userId, (id, active) -> {
            if (!active.sessionId().equals(sessionId)) {
                return active;
            }
            removed[0] = active;
            return null;
        });
        if (removed[0] == null) {
            return Optional.empty();
        }
        usersBySessionId.remove(sessionId, userId);
        return Optional.of(new RemovedSession(userId, removed[0]));
    }

    /**
     * 사용자의 활성 세션을 무조건 제거한다(계정 정지 등 관리자 조치용).
     */
    public Optional<ActiveSession> removeByUserId(UUID userId) {
        ActiveSession removed = sessionsByUser.remove(userId);
        if (removed == null) {
            return Optional.empty();
        }
        usersBySessionId.remove(removed.sessionId(), userId);
        return Optional.of(removed);
    }

    /**
     * sessionId가 여전히 사용자의 활성 세션일 때만 SSE 연결을 원자적으로 교체한다. {@code sessionsByUser.compute()} 안에서는
     * {@link ActiveSession} 값만 바꿔치기하고, 반환된 {@link SseReplacement#previous()}의 emitter 종료는 반드시 호출자가
     * compute 밖에서 수행해야 한다 — {@code SseEmitter.complete()}가 즉시 completion 콜백을 실행할 수 있고, 그 콜백이 같은
     * userId로 다시 이 registry를 건드리면 compute 재진입 문제가 생기기 때문이다.
     */
    public SseReplacement replaceSseIfCurrent(UUID userId, String sessionId, SseConnection newConnection) {
        SseConnection[] previous = new SseConnection[1];
        boolean[] registered = new boolean[1];

        sessionsByUser.computeIfPresent(userId, (id, active) -> {
            if (!active.sessionId().equals(sessionId)) {
                return active;
            }
            previous[0] = active.sseConnection();
            registered[0] = true;
            return active.withSseConnection(newConnection);
        });

        return new SseReplacement(registered[0], previous[0]);
    }

    /**
     * userId·sessionId·connectionId가 모두 현재 상태와 일치할 때만 SSE 연결을 제거한다. 셋 중 하나라도 어긋나면(이미 다른
     * 세션으로 교체됐거나, 이미 다른 연결로 교체됐거나) no-op이라 늦게 도착한 이전 연결의 completion/disconnect가 새 연결을
     * 지우지 못한다.
     */
    public Optional<SseConnection> removeSseIfCurrent(UUID userId, String sessionId, String connectionId) {
        SseConnection[] removed = new SseConnection[1];

        sessionsByUser.computeIfPresent(userId, (id, active) -> {
            SseConnection current = active.sseConnection();
            if (!active.sessionId().equals(sessionId)
                    || current == null
                    || !current.connectionId().equals(connectionId)) {
                return active;
            }
            removed[0] = current;
            return active.withoutSseConnection();
        });

        return Optional.ofNullable(removed[0]);
    }

    /**
     * 사용자의 현재 SSE 연결을 조회한다(없으면 empty).
     */
    public Optional<SseConnection> findSse(UUID userId) {
        return Optional.ofNullable(sessionsByUser.get(userId)).map(ActiveSession::sseConnection);
    }

    /**
     * 사용자에게 현재 SSE 연결이 있는지 락 없이 조회한다.
     */
    public boolean isSseConnected(UUID userId) {
        ActiveSession active = sessionsByUser.get(userId);
        return active != null && active.sseConnection() != null;
    }

    /**
     * sessionId가 여전히 사용자의 활성 세션인지 조회한다.
     */
    public boolean isCurrent(UUID userId, String sessionId) {
        ActiveSession active = sessionsByUser.get(userId);
        return active != null && active.sessionId().equals(sessionId);
    }

    /**
     * SSE 연결을 보유한 활성 세션들의 heartbeat용 스냅샷. 내부 맵을 직접 노출하지 않고, heartbeat가 순회하는 동안 registry가
     * 잠기지 않도록 독립된 목록으로 복사해 돌려준다.
     */
    public List<ActiveSessionSnapshot> snapshotSseConnections() {
        return sessionsByUser.entrySet().stream()
                .filter(entry -> entry.getValue().sseConnection() != null)
                .map(entry -> new ActiveSessionSnapshot(
                        entry.getKey(), entry.getValue().sessionId(), entry.getValue().sseConnection()))
                .toList();
    }

    /**
     * 두 맵의 현황. 사용자당 세션이 하나이므로 정상 상태에서는 두 크기가 같아야 한다. {@code usersBySessionId} 쪽만 계속 커지면 역인덱스를 정리하지 못하는 경로가 있다는 뜻이므로,
     * 두 값을 나란히 놓고 보는 것 자체가 진단이다.
     *
     * <p>{@code usersBySessionId}의 키는 세션 ID라 노출하면 세션 탈취에 그대로 쓰일 수 있어 샘플을 내보내지 않는다.
     */
    @Override
    public List<InMemoryStructureDto> inMemoryStructures() {
        long sseConnected = sessionsByUser.values().stream()
                .filter(active -> active.sseConnection() != null)
                .count();

        return List.of(
                InMemoryStructureDto.ofMap("sessionsByUser", "userId → 활성 세션", sessionsByUser)
                        .withBreakdown(Map.of("SSE 연결 보유", sseConnected)),
                InMemoryStructureDto.ofSize("usersBySessionId", "세션 ID → userId 역인덱스 (키 미노출)",
                        usersBySessionId.size()));
    }

    /**
     * {@link #removeIfCurrent}가 제거한 세션과 그 사용자. 로그아웃/세션 만료 호출자가 {@code activeSession()} 안의 SSE
     * 연결을 종료할 수 있도록 세션 전체를 함께 돌려준다.
     */
    public record RemovedSession(UUID userId, ActiveSession activeSession) {
    }

    /**
     * {@link #replaceSseIfCurrent}의 결과. {@code registered}가 false면 sessionId 불일치 또는 활성 세션 없음으로
     * 등록이 거부된 것이라 {@code previous}는 항상 null이다. true면 등록에 성공한 것이고, 이전 연결이 있었다면
     * {@code previous}에 담겨 있으니 호출자가 종료해야 한다.
     */
    public record SseReplacement(boolean registered, SseConnection previous) {
    }

    /**
     * {@link #snapshotSseConnections}가 담는 SSE 연결 보유 세션 하나.
     */
    public record ActiveSessionSnapshot(UUID userId, String sessionId, SseConnection sseConnection) {
    }
}
