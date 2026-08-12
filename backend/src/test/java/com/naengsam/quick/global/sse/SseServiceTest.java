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
 * SSE 파사드가 전송을 별도 스레드로 오프로딩해 레지스트리에 위임하는지 검증한다.
 */
class SseServiceTest {

    private SseEmitterRegistry registry;
    private SseService sseService;

    @BeforeEach
    void setUp() {
        registry = mock(SseEmitterRegistry.class);
        sseService = new SseService(registry);
    }

    @AfterEach
    void tearDown() {
        sseService.shutdown();
    }

    @Test
    void send하면_이벤트이름과_함께_레지스트리로_전송을_위임한다() {
        UUID userId = UUID.randomUUID();
        SseEventType type = () -> "test_event";
        Object payload = new Object();

        sseService.send(userId, type, payload);

        // 전송은 별도 스레드에서 비동기로 일어나므로 timeout 검증을 사용한다.
        verify(registry, timeout(1000)).send(eq(userId), eq("test_event"), any());
    }

    @Test
    void subscribe하면_사용자의_새_emitter_연결을_레지스트리에_위임한다() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        given(registry.connect(userId)).willReturn(emitter);

        SseEmitter result = sseService.subscribe(userId);

        assertThat(result).isSameAs(emitter);
        verify(registry).connect(userId);
    }

    @Test
    void isConnected는_사용자의_연결_상태_조회를_레지스트리에_위임한다() {
        UUID userId = UUID.randomUUID();
        given(registry.isConnected(userId)).willReturn(true);

        boolean connected = sseService.isConnected(userId);

        assertThat(connected).isTrue();
        verify(registry).isConnected(userId);
    }
}
