package com.naengsam.quick.global.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseEmitterRegistry;
import jakarta.servlet.http.HttpSessionEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * 서블릿 컨테이너의 세션 만료(timeout) 콜백에서 활성 세션에 해당하는 사용자만 골라 SSE를 종료하는지 검증한다.
 */
class SessionExpirationListenerTest {

    private ActiveSessionRegistry activeSessionRegistry;
    private SseEmitterRegistry sseEmitterRegistry;
    private SessionExpirationListener listener;

    @BeforeEach
    void setUp() {
        activeSessionRegistry = new ActiveSessionRegistry();
        sseEmitterRegistry = mock(SseEmitterRegistry.class);
        listener = new SessionExpirationListener(activeSessionRegistry, sseEmitterRegistry);
    }

    @Test
    void 세션_timeout이면_해당_사용자의_모든_emitter를_종료한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = loginSession(userId);

        listener.sessionDestroyed(sessionDestroyedEvent(session));

        verify(sseEmitterRegistry).disconnectAll(userId, SseCloseReason.SESSION_EXPIRED);
    }

    @Test
    void 이전_세션의_늦은_sessionDestroyed는_새_세션에_영향을_주지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession firstSession = loginSession(userId);
        loginSession(userId);

        listener.sessionDestroyed(sessionDestroyedEvent(firstSession));

        verify(sseEmitterRegistry, never()).disconnectAll(any(), any());
    }

    @Test
    void 로그아웃으로_이미_제거된_세션의_뒤이은_sessionDestroyed는_중복_종료하지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = loginSession(userId);
        // 컨트롤러의 로그아웃 흐름: removeIfCurrent로 먼저 제거 후 disconnectAll 호출.
        activeSessionRegistry.removeIfCurrent(session.getSessionId())
                .ifPresent(id -> sseEmitterRegistry.disconnectAll(id, SseCloseReason.LOGOUT));

        listener.sessionDestroyed(sessionDestroyedEvent(session));

        verify(sseEmitterRegistry).disconnectAll(eq(userId), eq(SseCloseReason.LOGOUT));
        verify(sseEmitterRegistry, never()).disconnectAll(userId, SseCloseReason.SESSION_EXPIRED);
    }

    @Test
    void 다른_사용자의_세션_만료는_영향을_주지_않는다() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        loginSession(userA);
        LoginSession sessionB = loginSession(userB);

        listener.sessionDestroyed(sessionDestroyedEvent(sessionB));

        verify(sseEmitterRegistry, never()).disconnectAll(eq(userA), any());
        verify(sseEmitterRegistry).disconnectAll(userB, SseCloseReason.SESSION_EXPIRED);
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
