package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 파사드가 전송을 별도 스레드로 오프로딩해 연결 관리자에 위임하는지 검증한다.
 */
class SseServiceTest {

    private SseConnectionManager connectionManager;
    private SseService sseService;

    @BeforeEach
    void setUp() {
        connectionManager = mock(SseConnectionManager.class);
        sseService = new SseService(connectionManager);
    }

    @AfterEach
    void tearDown() {
        sseService.shutdown();
    }

    @Test
    void send하면_이벤트이름과_함께_연결_관리자로_전송을_위임한다() {
        UUID userId = UUID.randomUUID();
        SseEventType type = () -> "test_event";
        Object payload = new Object();

        sseService.send(userId, type, payload);

        // 전송은 별도 스레드에서 비동기로 일어나므로 timeout 검증을 사용한다.
        verify(connectionManager, timeout(1000)).send(eq(userId), eq("test_event"), any());
    }

    @Test
    void subscribe하면_userId와_sessionId로_연결_수립을_위임한다() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-1";
        SseEmitter emitter = mock(SseEmitter.class);
        given(connectionManager.connect(userId, sessionId)).willReturn(emitter);

        SseEmitter result = sseService.subscribe(userId, sessionId);

        assertThat(result).isSameAs(emitter);
        verify(connectionManager).connect(userId, sessionId);
    }

    @Test
    void disconnect하면_userId_sessionId_connectionId를_그대로_위임한다() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-1";
        String connectionId = "connection-1";

        sseService.disconnect(userId, sessionId, connectionId);

        verify(connectionManager).disconnect(userId, sessionId, connectionId);
    }

    @Test
    void isConnected는_사용자의_연결_상태_조회를_연결_관리자에_위임한다() {
        UUID userId = UUID.randomUUID();
        given(connectionManager.isConnected(userId)).willReturn(true);

        boolean connected = sseService.isConnected(userId);

        assertThat(connected).isTrue();
        verify(connectionManager).isConnected(userId);
    }
}
