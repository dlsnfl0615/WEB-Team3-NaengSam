package com.naengsam.quick.global.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseConnection;
import com.naengsam.quick.global.sse.SseConnectionManager;
import jakarta.servlet.http.HttpSessionEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 서블릿 컨테이너의 세션 만료(timeout) 콜백에서 활성 세션에 해당하는 사용자만 골라 SSE를 종료하는지 검증한다.
 */
class SessionExpirationListenerTest {

    private ActiveSessionRegistry activeSessionRegistry;
    private SseConnectionManager sseConnectionManager;
    private SessionExpirationListener listener;

    @BeforeEach
    void setUp() {
        activeSessionRegistry = new ActiveSessionRegistry();
        sseConnectionManager = mock(SseConnectionManager.class);
        listener = new SessionExpirationListener(activeSessionRegistry, sseConnectionManager);
    }

    @Test
    void 세션_timeout이면_해당_사용자의_SSE_연결을_종료한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = loginSession(userId);
        SseConnection connection = attachSseConnection(userId, session);

        listener.sessionDestroyed(sessionDestroyedEvent(session));

        verify(sseConnectionManager).close(connection, SseCloseReason.SESSION_EXPIRED);
    }

    @Test
    void SSE_연결이_없으면_종료를_시도하지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = loginSession(userId);

        listener.sessionDestroyed(sessionDestroyedEvent(session));

        verify(sseConnectionManager, never()).close(any(), any());
    }

    @Test
    void 이전_세션의_늦은_sessionDestroyed는_새_세션에_영향을_주지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession firstSession = loginSession(userId);
        LoginSession secondSession = loginSession(userId);
        attachSseConnection(userId, secondSession);

        listener.sessionDestroyed(sessionDestroyedEvent(firstSession));

        verify(sseConnectionManager, never()).close(any(), any());
    }

    @Test
    void 로그아웃으로_이미_제거된_세션의_뒤이은_sessionDestroyed는_중복_종료하지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = loginSession(userId);
        SseConnection connection = attachSseConnection(userId, session);
        // 컨트롤러의 로그아웃 흐름: removeIfCurrent로 먼저 제거 후 SSE를 종료.
        activeSessionRegistry.removeIfCurrent(session.getSessionId())
                .ifPresent(removed -> sseConnectionManager.close(connection, SseCloseReason.LOGOUT));

        listener.sessionDestroyed(sessionDestroyedEvent(session));

        verify(sseConnectionManager).close(eq(connection), eq(SseCloseReason.LOGOUT));
        verify(sseConnectionManager, never()).close(connection, SseCloseReason.SESSION_EXPIRED);
    }

    @Test
    void 다른_사용자의_세션_만료는_영향을_주지_않는다() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        LoginSession sessionA = loginSession(userA);
        SseConnection connectionA = attachSseConnection(userA, sessionA);
        LoginSession sessionB = loginSession(userB);
        SseConnection connectionB = attachSseConnection(userB, sessionB);

        listener.sessionDestroyed(sessionDestroyedEvent(sessionB));

        verify(sseConnectionManager, never()).close(eq(connectionA), any());
        verify(sseConnectionManager).close(connectionB, SseCloseReason.SESSION_EXPIRED);
    }

    private SseConnection attachSseConnection(UUID userId, LoginSession session) {
        SseConnection connection = new SseConnection(UUID.randomUUID().toString(), mock(SseEmitter.class));
        activeSessionRegistry.replaceSseIfCurrent(userId, session.getSessionId(), connection);
        return connection;
    }

    private LoginSession loginSession(UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginSession session = LoginSession.create(request);
        session.login(userId);
        activeSessionRegistry.replace(userId, session);
        return session;
    }

    private HttpSessionEvent sessionDestroyedEvent(LoginSession session) {
        MockHttpSession httpSession = new MockHttpSession(null, session.getSessionId());
        return new HttpSessionEvent(httpSession);
    }
}
