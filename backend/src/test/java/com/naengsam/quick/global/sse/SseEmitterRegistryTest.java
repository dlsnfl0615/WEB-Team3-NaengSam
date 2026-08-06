package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자별 SSE 연결 저장소가 전송/정리와 메트릭 집계를 올바르게 하는지 검증한다.
 */
class SseEmitterRegistryTest {

    private MeterRegistry meterRegistry;
    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registry = new SseEmitterRegistry(meterRegistry);
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

    @Test
    void 연결하면_opened가_증가하고_active게이지가_1이_된다() {
        registry.connect(UUID.randomUUID());

        assertThat(counter("sse.connections.opened")).isEqualTo(1.0);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 같은_사용자가_재연결하면_closed_replaced가_증가한다() {
        UUID userId = UUID.randomUUID();

        registry.connect(userId);
        registry.connect(userId);

        assertThat(counter("sse.connections.opened")).isEqualTo(2.0);
        assertThat(counter("sse.connections.closed", "reason", "replaced")).isEqualTo(1.0);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void send_성공시_이벤트이름_태그로_sent가_증가한다() {
        UUID userId = UUID.randomUUID();
        emitters().put(userId, mock(SseEmitter.class));

        registry.send(userId, "offer_popup", Map.of());

        assertThat(counter("sse.events.sent", "event", "offer_popup")).isEqualTo(1.0);
    }

    @Test
    void 미연결_사용자에게_send하면_dropped_not_connected가_증가한다() {
        registry.send(UUID.randomUUID(), "offer_popup", Map.of());

        assertThat(counter("sse.events.dropped", "reason", "not_connected")).isEqualTo(1.0);
    }

    @Test
    void send중_IOException이_나면_closed_send_failed와_dropped_send_failed가_각각_1이다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));
        emitters().put(userId, emitter);

        registry.send(userId, "offer_popup", Map.of());

        assertThat(counter("sse.events.dropped", "reason", "send_failed")).isEqualTo(1.0);
        assertThat(counter("sse.connections.closed", "reason", "send_failed")).isEqualTo(1.0);
    }

    private double counter(String name, String... tags) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, SseEmitter> emitters() {
        return (Map<UUID, SseEmitter>) ReflectionTestUtils.getField(registry, "emitters");
    }
}
