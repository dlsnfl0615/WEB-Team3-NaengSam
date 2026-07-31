package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자별 SSE 연결 저장소가 전송/정리를 올바르게 하는지 검증한다.
 */
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void 연결하면_emitter를_반환하고_레지스트리에_등록한다() {
        UUID userId = UUID.randomUUID();

        SseEmitter emitter = registry.connect(userId);

        assertThat(emitter).isNotNull();
        assertThat(emitters()).containsKey(userId);
    }

    @Test
    void send는_연결된_사용자에게만_전송한다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        emitters().put(userId, emitter);

        registry.send(userId, "offer_popup", Map.of());

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 미연결_사용자에게_send하면_아무것도_하지_않는다() {
        UUID unknown = UUID.randomUUID();

        registry.send(unknown, "offer_popup", Map.of());

        assertThat(emitters()).doesNotContainKey(unknown);
    }

    @Test
    void send중_IOException이_나면_emitter를_제거한다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));
        emitters().put(userId, emitter);

        registry.send(userId, "offer_popup", Map.of());

        verify(emitter).completeWithError(any());
        assertThat(emitters()).doesNotContainKey(userId);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, SseEmitter> emitters() {
        return (Map<UUID, SseEmitter>) ReflectionTestUtils.getField(registry, "emitters");
    }
}
