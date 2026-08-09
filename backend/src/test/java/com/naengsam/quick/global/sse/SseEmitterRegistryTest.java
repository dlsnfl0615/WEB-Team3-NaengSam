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
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자·연결별 SSE 저장소가 전송/정리와 메트릭 집계를 올바르게 하는지 검증한다.
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
        assertThat(connectionsOf(userId)).containsValue(emitter);
    }

    @Test
    void send는_연결된_사용자에게만_전송한다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter);

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
        connectionsOf(userId).put("connection-1", emitter);

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
    void 같은_사용자가_두번_연결하면_두_emitter가_모두_유지된다() {
        UUID userId = UUID.randomUUID();

        SseEmitter first = registry.connect(userId);
        SseEmitter second = registry.connect(userId);

        assertThat(connectionsOf(userId).values()).containsExactlyInAnyOrder(first, second);
        assertThat(counter("sse.connections.opened")).isEqualTo(2.0);
        assertThat(meterRegistry.find("sse.connections.closed").tags("reason", "replaced").counter()).isNull();
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void send는_같은_사용자의_모든_연결에_브로드캐스트된다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter1);
        connectionsOf(userId).put("connection-2", emitter2);

        registry.send(userId, "offer_popup", Map.of());

        verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 한_연결의_전송이_실패해도_다른_연결은_유지된다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter failed = mock(SseEmitter.class);
        SseEmitter active = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(failed).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", failed);
        connectionsOf(userId).put("connection-2", active);

        registry.send(userId, "offer_popup", Map.of());

        verify(active).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(connectionsOf(userId)).containsEntry("connection-2", active)
                .doesNotContainKey("connection-1");
    }

    @Test
    void 마지막_연결까지_제거되면_사용자_엔트리도_함께_제거된다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", emitter);

        registry.send(userId, "offer_popup", Map.of());

        assertThat(emitters()).doesNotContainKey(userId);
    }

    @Test
    void send_성공시_이벤트이름_태그로_sent가_증가한다() {
        UUID userId = UUID.randomUUID();
        connectionsOf(userId).put("connection-1", mock(SseEmitter.class));

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
        connectionsOf(userId).put("connection-1", emitter);

        registry.send(userId, "offer_popup", Map.of());

        assertThat(counter("sse.events.dropped", "reason", "send_failed")).isEqualTo(1.0);
        assertThat(counter("sse.connections.closed", "reason", "send_failed")).isEqualTo(1.0);
    }

    private double counter(String name, String... tags) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Map<String, SseEmitter>> emitters() {
        return (Map<UUID, Map<String, SseEmitter>>) ReflectionTestUtils.getField(registry, "emitters");
    }

    private Map<String, SseEmitter> connectionsOf(UUID userId) {
        return emitters().computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
    }
}
