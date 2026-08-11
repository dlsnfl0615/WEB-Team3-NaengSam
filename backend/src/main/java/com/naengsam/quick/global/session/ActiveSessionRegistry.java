package com.naengsam.quick.global.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사용자당 활성 세션을 하나만 유지한다. 새로 로그인하면 {@link #replace}가 이전 활성 세션을 밀어내고 반환하므로, 호출자는 반환된
 * 세션을 이용해 이전 SSE 연결 종료와 이전 세션 무효화를 진행한다.
 */
@Slf4j
@Component
public class ActiveSessionRegistry {

    private final Map<UUID, ActiveSession> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> usersBySessionId = new ConcurrentHashMap<>();

    /**
     * 사용자의 활성 세션을 새 세션으로 교체하고 이전 세션을 반환한다(없으면 null). 동시에 여러 로그인 요청이 들어와도 사용자당 맵에
     * 순서대로 반영되는 마지막 {@code put}만 최종 상태로 남으므로, 뒤에 반영된 호출이 앞선 호출의 세션을 previous로 받아 정리하게 되어
     * 결과적으로 마지막 세션 하나만 남는다.
     */
    public ActiveSession replace(UUID userId, LoginSession newSession) {
        ActiveSession newEntry = new ActiveSession(newSession.getSessionId(), newSession);
        usersBySessionId.put(newEntry.sessionId(), userId);

        ActiveSession previous = sessionsByUser.put(userId, newEntry);
        if (previous != null && !previous.sessionId().equals(newEntry.sessionId())) {
            usersBySessionId.remove(previous.sessionId(), userId);
        }
        log.debug("사용자 활성 세션 교체: userId={}, 이전 세션 존재={}", userId, previous != null);
        return previous;
    }

    /**
     * 주어진 sessionId가 여전히 해당 사용자의 활성 세션일 때만 제거한다(이미 다른 로그인으로 교체됐으면 no-op).
     */
    public Optional<UUID> removeIfCurrent(String sessionId) {
        UUID userId = usersBySessionId.get(sessionId);
        if (userId == null) {
            return Optional.empty();
        }

        boolean[] removed = new boolean[1];
        sessionsByUser.computeIfPresent(userId, (id, active) -> {
            if (!active.sessionId().equals(sessionId)) {
                return active;
            }
            removed[0] = true;
            return null;
        });
        if (!removed[0]) {
            return Optional.empty();
        }
        usersBySessionId.remove(sessionId, userId);
        return Optional.of(userId);
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
}
