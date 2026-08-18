package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.ActiveSessionRegistry;
import com.naengsam.quick.global.session.LoginSession;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 계정당 최대 하나인 SSE 연결의 수립/교체/해제/전송이 {@link ActiveSessionRegistry}와 정합성 있게 동작하는지 검증한다.
 * 새 구독은 상한 검사 없이 항상 기존 연결을 즉시 교체하고, 늦게 도착한 이전 연결의 콜백/요청은 새 연결에 영향을 주지
 * 않아야 한다.
 */
class SseConnectionManagerTest {

    private ActiveSessionRegistry activeSessionRegistry;
    private MeterRegistry meterRegistry;
    private SseConnectionManager manager;

    @BeforeEach
    void setUp() {
        activeSessionRegistry = new ActiveSessionRegistry();
        meterRegistry = new SimpleMeterRegistry();
        manager = new SseConnectionManager(activeSessionRegistry, meterRegistry,
                new SseProperties(Duration.ofSeconds(25), Duration.ofHours(1)));
    }

    @Test
    void 연결하면_emitter를_반환하고_현재_연결로_등록된다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);

        SseEmitter emitter = manager.connect(userId, sessionId);

        assertThat(emitter).isNotNull();
        assertThat(activeSessionRegistry.isSseConnected(userId)).isTrue();
    }

    @Test
    void 연결하면_opened가_증가하고_active게이지가_1이_된다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);

        manager.connect(userId, sessionId);

        assertThat(counter("sse.connections.opened")).isEqualTo(1.0);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void connected_이벤트에_connectionId가_담긴다() throws IOException {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);

        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            manager.connect(userId, sessionId);

            SseEmitter created = mocked.constructed().get(0);
            String connectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();

            ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(created).send(captor.capture());
            boolean containsConnectionId = captor.getValue().build().stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .anyMatch(data -> Map.of("connectionId", connectionId).equals(data));
            assertThat(containsConnectionId).isTrue();
        }
    }

    @Test
    void 같은_사용자가_다시_연결하면_첫_emitter를_교체하고_active게이지는_1이다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);

        SseEmitter first = manager.connect(userId, sessionId);
        SseEmitter second = manager.connect(userId, sessionId);

        assertThat(second).isNotSameAs(first);
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 같은_사용자의_재연결은_replaced_사유로_closed_메트릭을_증가시킨다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        manager.connect(userId, sessionId);

        manager.connect(userId, sessionId);

        assertThat(counter("sse.connections.closed", "reason", "replaced")).isEqualTo(1.0);
    }

    @Test
    void 다른_사용자는_독립적으로_연결된다() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String sessionA = login(userA);
        String sessionB = login(userB);

        manager.connect(userA, sessionA);
        manager.connect(userB, sessionB);

        assertThat(activeSessionRegistry.isSseConnected(userA)).isTrue();
        assertThat(activeSessionRegistry.isSseConnected(userB)).isTrue();
        assertThat(meterRegistry.get("sse.connections.active").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void 이전_sessionId로는_구독할_수_없다() {
        UUID userId = UUID.randomUUID();
        String firstSessionId = login(userId);
        login(userId); // 다른 곳에서 로그인해 세션 교체

        assertThatThrownBy(() -> manager.connect(userId, firstSessionId))
                .isInstanceOf(BusinessException.class);
        assertThat(activeSessionRegistry.isSseConnected(userId)).isFalse();
    }

    @Test
    void send는_현재_연결에만_한번_전달된다() throws IOException {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        manager.connect(userId, sessionId);
        String initialConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();

        manager.send(userId, "offer_popup", Map.of());

        assertThat(activeSessionRegistry.findSse(userId).orElseThrow().connectionId()).isEqualTo(initialConnectionId);
        assertThat(counter("sse.events.sent", "event", "offer_popup")).isEqualTo(1.0);
    }

    @Test
    void 미연결_사용자에게_send하면_dropped_not_connected가_증가한다() {
        manager.send(UUID.randomUUID(), "offer_popup", Map.of());

        assertThat(counter("sse.events.dropped", "reason", "not_connected")).isEqualTo(1.0);
    }

    @Test
    void 이전_emitter의_늦은_completion_콜백은_새_연결을_제거하지_않는다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        SseEmitter first = manager.connect(userId, sessionId);
        manager.connect(userId, sessionId);
        String currentConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();

        // 이미 completion 콜백에서 registry 제거가 시도됐지만(교체 시 previous 쪽은 종료됐을 뿐 completion 콜백도 다시
        // 태울 수 있다), 첫 emitter의 completion 콜백이 뒤늦게 다시 불려도 현재 연결에는 영향이 없어야 한다.
        first.complete();

        assertThat(activeSessionRegistry.findSse(userId).orElseThrow().connectionId()).isEqualTo(currentConnectionId);
    }

    @Test
    void 이전_emitter의_늦은_send_failure는_새_연결을_제거하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        SseEmitter firstEmitter = mock(SseEmitter.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            assertThat(allowFailure.await(5, TimeUnit.SECONDS)).isTrue();
            throw new IOException("죽은 클라이언트");
        }).when(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        // registry에 mock emitter를 직접 심어, send 중 실패를 재현한다.
        activeSessionRegistry.replaceSseIfCurrent(userId, sessionId, new SseConnection("stale-connection", firstEmitter));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> send = executor.submit(() -> manager.send(userId, "offer_popup", Map.of()));
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            manager.connect(userId, sessionId);
            String newConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();
            allowFailure.countDown();
            send.get(5, TimeUnit.SECONDS);

            assertThat(activeSessionRegistry.findSse(userId).orElseThrow().connectionId()).isEqualTo(newConnectionId);
        } finally {
            allowFailure.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void heartbeat_실패와_새_연결_교체가_경합해도_새_연결이_유지된다() throws Exception {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        SseEmitter failingEmitter = mock(SseEmitter.class);
        CountDownLatch heartbeatStarted = new CountDownLatch(1);
        CountDownLatch allowFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            heartbeatStarted.countDown();
            assertThat(allowFailure.await(5, TimeUnit.SECONDS)).isTrue();
            throw new IOException("연결 끊김");
        }).when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));
        activeSessionRegistry.replaceSseIfCurrent(userId, sessionId, new SseConnection("stale-connection", failingEmitter));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> heartbeat = executor.submit(() -> manager.sendHeartbeats());
            assertThat(heartbeatStarted.await(5, TimeUnit.SECONDS)).isTrue();

            manager.connect(userId, sessionId);
            String newConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();
            allowFailure.countDown();
            heartbeat.get(5, TimeUnit.SECONDS);

            assertThat(activeSessionRegistry.findSse(userId).orElseThrow().connectionId()).isEqualTo(newConnectionId);
        } finally {
            allowFailure.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 늦은_disconnect_요청은_새_연결을_제거하지_않는다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        manager.connect(userId, sessionId);
        String staleConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();
        manager.connect(userId, sessionId);
        String currentConnectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();

        manager.disconnect(userId, sessionId, staleConnectionId);

        assertThat(activeSessionRegistry.findSse(userId)).isPresent();
        assertThat(activeSessionRegistry.findSse(userId).orElseThrow().connectionId()).isEqualTo(currentConnectionId);
    }

    @Test
    void 현재_연결의_disconnect는_제거하고_client_disconnect_메트릭을_증가시킨다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);
        manager.connect(userId, sessionId);
        String connectionId = activeSessionRegistry.findSse(userId).orElseThrow().connectionId();

        manager.disconnect(userId, sessionId, connectionId);

        assertThat(activeSessionRegistry.findSse(userId)).isEmpty();
        assertThat(counter("sse.connections.closed", "reason", "client_disconnect")).isEqualTo(1.0);
    }

    @Test
    void disconnect는_이미_연결이_없어도_idempotent하게_아무일도_하지_않는다() {
        UUID userId = UUID.randomUUID();
        String sessionId = login(userId);

        manager.disconnect(userId, sessionId, "존재하지-않는-connection");

        assertThat(activeSessionRegistry.findSse(userId)).isEmpty();
        assertThat(meterRegistry.find("sse.connections.closed").tags("reason", "client_disconnect").counter())
                .isNull();
    }

    private double counter(String name, String... tags) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }

    private String login(UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginSession session = LoginSession.create(request);
        session.login(userId);
        activeSessionRegistry.replace(userId, session);
        return session.getSessionId();
    }
}
