package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 사용자·연결별 SSE 저장소가 전송/정리와 메트릭 집계를 올바르게 하는지 검증한다.
 */
class SseEmitterRegistryTest {

    private static final int MAX_CONNECTIONS_PER_USER = 5;

    private MeterRegistry meterRegistry;
    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registry = new SseEmitterRegistry(meterRegistry,
                new SseProperties(Duration.ofSeconds(25), Duration.ofHours(1), MAX_CONNECTIONS_PER_USER));
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
    void isConnected는_연결이_하나라도_있는_사용자만_true를_반환한다() {
        UUID connected = UUID.randomUUID();
        UUID disconnected = UUID.randomUUID();
        connectionsOf(connected).put("connection-1", mock(SseEmitter.class));

        assertThat(registry.isConnected(connected)).isTrue();
        assertThat(registry.isConnected(disconnected)).isFalse();
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
    void send는_다른_사용자의_연결에는_전송하지_않는다() throws IOException {
        UUID targetUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        SseEmitter target = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        connectionsOf(targetUserId).put("target-connection", target);
        connectionsOf(otherUserId).put("other-connection", other);

        registry.send(targetUserId, "offer_popup", Map.of());

        verify(target).send(any(SseEmitter.SseEventBuilder.class));
        verify(other, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 여러번_send하면_모든_연결이_각_이벤트를_한번씩_받는다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter1);
        connectionsOf(userId).put("connection-2", emitter2);

        registry.send(userId, "first_event", Map.of("sequence", 1));
        registry.send(userId, "second_event", Map.of("sequence", 2));

        verify(emitter1, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(counter("sse.events.sent", "event", "first_event")).isEqualTo(2.0);
        assertThat(counter("sse.events.sent", "event", "second_event")).isEqualTo(2.0);
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
    void 전송_실패_정리와_새_연결이_동시에_일어나도_새_emitter는_유지된다() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter failed = mock(SseEmitter.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            if (!allowFailure.await(5, TimeUnit.SECONDS)) {
                throw new IOException("전송 실패 허용 대기 시간 초과");
            }
            throw new IOException("죽은 클라이언트");
        }).when(failed).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("failed-connection", failed);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> send = executor.submit(() -> registry.send(userId, "offer_popup", Map.of()));
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            SseEmitter fresh = registry.connect(userId);
            allowFailure.countDown();
            send.get(5, TimeUnit.SECONDS);

            assertThat(connectionsOf(userId)).containsValue(fresh);
            assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(1.0);
        } finally {
            allowFailure.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 상한까지는_연결에_성공하고_상한을_넘는_연결은_null로_거부한다() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < MAX_CONNECTIONS_PER_USER; i++) {
            assertThat(registry.connect(userId)).isNotNull();
        }
        SseEmitter overLimit = registry.connect(userId);

        assertThat(overLimit).isNull();
        assertThat(connectionsOf(userId)).hasSize(MAX_CONNECTIONS_PER_USER);
    }

    @Test
    void 한_사용자가_상한에_도달해도_다른_사용자는_독립적으로_상한까지_연결된다() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        for (int i = 0; i < MAX_CONNECTIONS_PER_USER; i++) {
            registry.connect(first);
        }
        assertThat(registry.connect(first)).isNull();

        for (int i = 0; i < MAX_CONNECTIONS_PER_USER; i++) {
            assertThat(registry.connect(second)).isNotNull();
        }
        assertThat(connectionsOf(first)).hasSize(MAX_CONNECTIONS_PER_USER);
        assertThat(connectionsOf(second)).hasSize(MAX_CONNECTIONS_PER_USER);
    }

    @Test
    void 거부된_연결은_opened와_active에서_제외되고_rejected만_증가한다() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < MAX_CONNECTIONS_PER_USER; i++) {
            registry.connect(userId);
        }
        registry.connect(userId);
        registry.connect(userId);

        assertThat(counter("sse.connections.opened")).isEqualTo(MAX_CONNECTIONS_PER_USER);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(MAX_CONNECTIONS_PER_USER);
        assertThat(counter("sse.connections.rejected")).isEqualTo(2.0);
    }

    @Test
    void 상한_도달후_기존_연결이_종료되면_빈_자리로_새_연결에_성공한다() {
        UUID userId = UUID.randomUUID();
        List<SseEmitter> connected = new ArrayList<>();
        for (int i = 0; i < MAX_CONNECTIONS_PER_USER; i++) {
            connected.add(registry.connect(userId));
        }
        assertThat(registry.connect(userId)).isNull();

        // 기존 연결 하나가 정상 종료(onCompletion)되어 자리가 빈 상황을 재현한다.
        String freedConnectionId = connectionsOf(userId).entrySet().stream()
                .filter(e -> e.getValue() == connected.get(0))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        ReflectionTestUtils.invokeMethod(
                registry, "remove", userId, freedConnectionId, connected.get(0), "completion");

        assertThat(connectionsOf(userId)).hasSize(MAX_CONNECTIONS_PER_USER - 1);
        assertThat(registry.connect(userId)).isNotNull();
        assertThat(connectionsOf(userId)).hasSize(MAX_CONNECTIONS_PER_USER);
    }

    @Test
    void 여러_스레드에서_동시에_연결해도_정확히_상한개만_등록된다() throws Exception {
        UUID userId = UUID.randomUUID();
        int connectionCount = 100;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<SseEmitter>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < connectionCount; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return registry.connect(userId);
                }));
            }
            start.countDown();

            long accepted = 0;
            for (Future<SseEmitter> future : futures) {
                if (future.get(5, TimeUnit.SECONDS) != null) {
                    accepted++;
                }
            }

            assertThat(accepted).isEqualTo(MAX_CONNECTIONS_PER_USER);
            assertThat(connectionsOf(userId)).hasSize(MAX_CONNECTIONS_PER_USER);
            assertThat(counter("sse.connections.opened")).isEqualTo(MAX_CONNECTIONS_PER_USER);
            assertThat(counter("sse.connections.rejected")).isEqualTo(connectionCount - MAX_CONNECTIONS_PER_USER);
            assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(MAX_CONNECTIONS_PER_USER);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void active_게이지는_사용자_수가_아니라_전체_연결_수를_집계한다() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        registry.connect(firstUserId);
        registry.connect(firstUserId);
        registry.connect(secondUserId);

        assertThat(emitters()).hasSize(2);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(3.0);
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

    @Test
    void disconnectAll은_해당_사용자의_모든_emitter를_종료한다() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter1);
        connectionsOf(userId).put("connection-2", emitter2);

        registry.disconnectAll(userId, SseCloseReason.LOGOUT);

        verify(emitter1).complete();
        verify(emitter2).complete();
        assertThat(emitters()).doesNotContainKey(userId);
    }

    @Test
    void disconnectAll은_다른_사용자의_emitter는_유지한다() {
        UUID targetUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        SseEmitter target = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        connectionsOf(targetUserId).put("target-connection", target);
        connectionsOf(otherUserId).put("other-connection", other);

        registry.disconnectAll(targetUserId, SseCloseReason.LOGOUT);

        verify(target).complete();
        verify(other, never()).complete();
        assertThat(connectionsOf(otherUserId)).containsEntry("other-connection", other);
    }

    @Test
    void 연결이_없는_사용자를_disconnectAll해도_아무일도_일어나지_않는다() {
        UUID unknown = UUID.randomUUID();

        registry.disconnectAll(unknown, SseCloseReason.LOGOUT);

        assertThat(emitters()).doesNotContainKey(unknown);
    }

    @Test
    void disconnectAll은_연결_수만큼_closed_메트릭을_증가시킨다() {
        UUID userId = UUID.randomUUID();
        connectionsOf(userId).put("connection-1", mock(SseEmitter.class));
        connectionsOf(userId).put("connection-2", mock(SseEmitter.class));

        registry.disconnectAll(userId, SseCloseReason.SESSION_EXPIRED);

        assertThat(counter("sse.connections.closed", "reason", "session_expired")).isEqualTo(2.0);
    }

    @Test
    void disconnectAll_이후_completion_콜백이_다시_불려도_closed_메트릭이_중복_집계되지_않는다() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter);

        registry.disconnectAll(userId, SseCloseReason.LOGOUT);
        // complete()가 실제로 onCompletion 콜백을 다시 태우는 상황을 재현: 이미 맵에서 제거됐으므로 no-op이어야 한다.
        ReflectionTestUtils.invokeMethod(registry, "remove", userId, "connection-1", emitter, "completion");

        assertThat(counter("sse.connections.closed", "reason", "logout")).isEqualTo(1.0);
        assertThat(meterRegistry.find("sse.connections.closed").tags("reason", "completion").counter()).isNull();
    }

    @Test
    void disconnectAll과_send가_동시에_일어나도_예외없이_안전하다() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            allowSendToFinish.await(5, TimeUnit.SECONDS);
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", emitter);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> send = executor.submit(() -> registry.send(userId, "offer_popup", Map.of()));
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            registry.disconnectAll(userId, SseCloseReason.LOGOUT);
            allowSendToFinish.countDown();
            send.get(5, TimeUnit.SECONDS);

            verify(emitter).complete();
            assertThat(emitters()).doesNotContainKey(userId);
        } finally {
            allowSendToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sendHeartbeats는_모든_connection에_heartbeat_주석을_보낸다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        connectionsOf(userId).put("connection-1", emitter1);
        connectionsOf(userId).put("connection-2", emitter2);

        registry.sendHeartbeats();

        verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendHeartbeats는_실패한_connection만_제거하고_나머지는_유지한다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter failed = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(failed).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", failed);
        connectionsOf(userId).put("connection-2", alive);

        registry.sendHeartbeats();

        verify(failed).completeWithError(any());
        assertThat(connectionsOf(userId)).containsEntry("connection-2", alive)
                .doesNotContainKey("connection-1");
    }

    @Test
    void sendHeartbeats_실패시_같은_사용자의_다른_연결까지_끊지_않는다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter failed = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(failed).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", failed);
        connectionsOf(userId).put("connection-2", alive);

        registry.sendHeartbeats();

        verify(alive, never()).complete();
        verify(alive, never()).completeWithError(any());
    }

    @Test
    void sendHeartbeats_실패시_heartbeat_failed_사유로_closed_메트릭이_증가한다() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        willThrow(new IOException("죽은 클라이언트")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", emitter);

        registry.sendHeartbeats();

        assertThat(counter("sse.connections.closed", "reason", "heartbeat_failed")).isEqualTo(1.0);
    }

    @Test
    void heartbeat와_도메인_이벤트가_동시에_전송돼도_둘_다_안전하게_처리된다() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);
        doAnswer(invocation -> {
            bothStarted.countDown();
            allowSendToFinish.await(5, TimeUnit.SECONDS);
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        connectionsOf(userId).put("connection-1", emitter);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> domainSend = executor.submit(() -> registry.send(userId, "offer_popup", Map.of()));
            Future<?> heartbeat = executor.submit(() -> registry.sendHeartbeats());
            assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();

            allowSendToFinish.countDown();
            domainSend.get(5, TimeUnit.SECONDS);
            heartbeat.get(5, TimeUnit.SECONDS);

            assertThat(connectionsOf(userId)).containsEntry("connection-1", emitter);
        } finally {
            allowSendToFinish.countDown();
            executor.shutdownNow();
        }
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
