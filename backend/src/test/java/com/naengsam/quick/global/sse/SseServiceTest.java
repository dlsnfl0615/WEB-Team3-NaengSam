package com.naengsam.quick.global.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void send하면_이벤트이름과_함께_레지스트리로_전송을_위임한다() {
        UUID userId = UUID.randomUUID();
        SseEventType type = () -> "test_event";
        Object payload = new Object();

        sseService.send(userId, type, payload);

        // 전송은 별도 스레드에서 비동기로 일어나므로 timeout 검증을 사용한다.
        verify(registry, timeout(1000)).send(eq(userId), eq("test_event"), any());
    }
}
