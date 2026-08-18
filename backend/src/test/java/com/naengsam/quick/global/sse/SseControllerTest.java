package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.session.LoginSession;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독/해제 엔드포인트가 현재 servlet 세션의 sessionId를 연결 관리자로 그대로 전달하는지 검증한다. 연결 수는
 * 더 이상 상한이 없으므로 구독은 항상 emitter를 반환한다.
 */
@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock
    private SseService sseService;

    @InjectMocks
    private SseController sseController;

    @Test
    void 구독에_성공하면_emitter를_반환한다() {
        UUID userId = UUID.randomUUID();
        LoggedInRequest loggedIn = loggedInRequest(userId);
        SseEmitter emitter = new SseEmitter();
        given(sseService.subscribe(userId, loggedIn.sessionId())).willReturn(emitter);

        SseEmitter result = sseController.subscribe(userId, loggedIn.request());

        assertThat(result).isSameAs(emitter);
    }

    @Test
    void 구독은_현재_sessionId를_그대로_전달한다() {
        UUID userId = UUID.randomUUID();
        LoggedInRequest loggedIn = loggedInRequest(userId);
        given(sseService.subscribe(userId, loggedIn.sessionId())).willReturn(new SseEmitter());

        sseController.subscribe(userId, loggedIn.request());

        verify(sseService).subscribe(userId, loggedIn.sessionId());
    }

    @Test
    void disconnect는_userId_sessionId_connectionId를_그대로_전달한다() {
        UUID userId = UUID.randomUUID();
        LoggedInRequest loggedIn = loggedInRequest(userId);
        String connectionId = "connection-1";

        sseController.disconnect(userId, connectionId, loggedIn.request());

        verify(sseService).disconnect(userId, loggedIn.sessionId(), connectionId);
    }

    private LoggedInRequest loggedInRequest(UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginSession session = LoginSession.create(request);
        session.login(userId);
        return new LoggedInRequest(request, session.getSessionId());
    }

    private record LoggedInRequest(MockHttpServletRequest request, String sessionId) {
    }
}
