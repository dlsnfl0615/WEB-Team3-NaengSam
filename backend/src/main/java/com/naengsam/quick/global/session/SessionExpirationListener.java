package com.naengsam.quick.global.session;

import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseConnection;
import com.naengsam.quick.global.sse.SseConnectionManager;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 서블릿 컨테이너가 세션을 만료시킬 때(타임아웃) 해당 세션의 SSE 연결을 정리한다. 로그아웃도 세션을 무효화하므로 이 리스너를 함께
 * 태우지만, {@link ActiveSessionRegistry#removeIfCurrent}가 이미 로그아웃 시점에 활성 세션을 제거해두기 때문에
 * 중복 정리로 이어지지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SessionExpirationListener implements HttpSessionListener {

    private final ActiveSessionRegistry activeSessionRegistry;
    private final SseConnectionManager sseConnectionManager;

    /**
     * {@link ActiveSessionRegistry#removeIfCurrent}로 여전히 활성 세션일 때만 처리해, 이미 새 로그인으로 교체된
     * 이전 세션의 늦은 만료 콜백이 새 세션의 SSE 연결을 끊지 않도록 한다.
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        activeSessionRegistry.removeIfCurrent(event.getSession().getId())
                .ifPresent(removed -> {
                    SseConnection connection = removed.activeSession().sseConnection();
                    if (connection != null) {
                        sseConnectionManager.close(connection, SseCloseReason.SESSION_EXPIRED);
                    }
                });
    }
}
