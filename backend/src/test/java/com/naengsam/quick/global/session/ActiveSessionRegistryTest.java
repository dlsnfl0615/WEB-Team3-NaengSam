package com.naengsam.quick.global.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.global.session.ActiveSessionRegistry.RemovedSession;
import com.naengsam.quick.global.session.ActiveSessionRegistry.SseReplacement;
import com.naengsam.quick.global.sse.SseConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자당 활성 세션을 하나, 세션당 SSE 연결을 하나만 유지하는 {@link ActiveSessionRegistry}의 교체/제거 동작을 검증한다.
 */
class ActiveSessionRegistryTest {

    private final ActiveSessionRegistry registry = new ActiveSessionRegistry();

    @Test
    void 첫_로그인은_활성_세션_하나를_등록하고_이전_세션은_없다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();

        ActiveSession previous = registry.replace(userId, session);

        assertThat(previous).isNull();
        assertThat(sessionsByUser()).containsKey(userId);
    }

    @Test
    void 새_로그인의_ActiveSession은_SSE_슬롯이_비어있다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();

        registry.replace(userId, session);

        assertThat(sessionsByUser().get(userId).sseConnection()).isNull();
    }

    @Test
    void 두번째_로그인은_첫_세션을_이전값으로_반환한다() {
        UUID userId = UUID.randomUUID();
        LoginSession first = newSession();
        LoginSession second = newSession();
        registry.replace(userId, first);

        ActiveSession previous = registry.replace(userId, second);

        assertThat(previous.session()).isSameAs(first);
        assertThat(sessionsByUser().get(userId).session()).isSameAs(second);
    }

    @Test
    void removeIfCurrent은_현재_세션이면_제거된_세션과_userId를_반환하고_제거한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);

        Optional<RemovedSession> removed = registry.removeIfCurrent(session.getSessionId());

        assertThat(removed).isPresent();
        assertThat(removed.get().userId()).isEqualTo(userId);
        assertThat(removed.get().activeSession().session()).isSameAs(session);
        assertThat(sessionsByUser()).doesNotContainKey(userId);
        assertThat(usersBySessionId()).doesNotContainKey(session.getSessionId());
    }

    @Test
    void removeIfCurrent은_이미_교체된_이전_세션이면_no_op이다() {
        UUID userId = UUID.randomUUID();
        LoginSession first = newSession();
        LoginSession second = newSession();
        registry.replace(userId, first);
        registry.replace(userId, second);

        Optional<RemovedSession> removed = registry.removeIfCurrent(first.getSessionId());

        assertThat(removed).isEmpty();
        assertThat(sessionsByUser().get(userId).session()).isSameAs(second);
    }

    @Test
    void removeIfCurrent은_세션에_담긴_SseConnection도_함께_반환한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        SseConnection connection = newConnection();
        registry.replaceSseIfCurrent(userId, session.getSessionId(), connection);

        Optional<RemovedSession> removed = registry.removeIfCurrent(session.getSessionId());

        assertThat(removed).isPresent();
        assertThat(removed.get().activeSession().sseConnection()).isSameAs(connection);
    }

    @Test
    void removeByUserId는_사용자의_활성_세션을_제거하고_반환한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);

        Optional<ActiveSession> removed = registry.removeByUserId(userId);

        assertThat(removed).isPresent();
        assertThat(removed.get().session()).isSameAs(session);
        assertThat(sessionsByUser()).doesNotContainKey(userId);
        assertThat(usersBySessionId()).doesNotContainKey(session.getSessionId());
    }

    @Test
    void removeByUserId는_세션이_없으면_empty를_반환한다() {
        Optional<ActiveSession> removed = registry.removeByUserId(UUID.randomUUID());

        assertThat(removed).isEmpty();
    }

    @Test
    void 현재_sessionId로_SSE_등록에_성공한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        SseConnection connection = newConnection();

        SseReplacement result = registry.replaceSseIfCurrent(userId, session.getSessionId(), connection);

        assertThat(result.registered()).isTrue();
        assertThat(result.previous()).isNull();
        assertThat(registry.findSse(userId)).contains(connection);
        assertThat(registry.isSseConnected(userId)).isTrue();
    }

    @Test
    void 같은_sessionId의_두번째_SSE는_이전_연결을_반환하고_새_연결로_교체한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        SseConnection first = newConnection();
        SseConnection second = newConnection();
        registry.replaceSseIfCurrent(userId, session.getSessionId(), first);

        SseReplacement result = registry.replaceSseIfCurrent(userId, session.getSessionId(), second);

        assertThat(result.registered()).isTrue();
        assertThat(result.previous()).isSameAs(first);
        assertThat(registry.findSse(userId)).contains(second);
    }

    @Test
    void 다른_sessionId는_SSE를_등록하지_못한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);

        SseReplacement result = registry.replaceSseIfCurrent(userId, "다른-session-id", newConnection());

        assertThat(result.registered()).isFalse();
        assertThat(result.previous()).isNull();
        assertThat(registry.findSse(userId)).isEmpty();
    }

    @Test
    void connectionId가_다른_늦은_제거_요청은_현재_연결을_제거하지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        SseConnection current = newConnection();
        registry.replaceSseIfCurrent(userId, session.getSessionId(), current);

        Optional<SseConnection> removed = registry.removeSseIfCurrent(userId, session.getSessionId(), "다른-connection-id");

        assertThat(removed).isEmpty();
        assertThat(registry.findSse(userId)).contains(current);
    }

    @Test
    void 이전_세션의_제거_요청은_새_세션의_연결을_제거하지_않는다() {
        UUID userId = UUID.randomUUID();
        LoginSession first = newSession();
        registry.replace(userId, first);
        SseConnection staleConnection = newConnection();
        registry.replaceSseIfCurrent(userId, first.getSessionId(), staleConnection);

        LoginSession second = newSession();
        registry.replace(userId, second);
        SseConnection currentConnection = newConnection();
        registry.replaceSseIfCurrent(userId, second.getSessionId(), currentConnection);

        Optional<SseConnection> removed =
                registry.removeSseIfCurrent(userId, first.getSessionId(), staleConnection.connectionId());

        assertThat(removed).isEmpty();
        assertThat(registry.findSse(userId)).contains(currentConnection);
    }

    @Test
    void 일치하는_요청은_현재_연결을_제거한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        SseConnection connection = newConnection();
        registry.replaceSseIfCurrent(userId, session.getSessionId(), connection);

        Optional<SseConnection> removed =
                registry.removeSseIfCurrent(userId, session.getSessionId(), connection.connectionId());

        assertThat(removed).contains(connection);
        assertThat(registry.findSse(userId)).isEmpty();
        assertThat(registry.isSseConnected(userId)).isFalse();
    }

    @Test
    void isCurrent은_현재_활성_세션일_때만_true다() {
        UUID userId = UUID.randomUUID();
        LoginSession first = newSession();
        registry.replace(userId, first);
        LoginSession second = newSession();
        registry.replace(userId, second);

        assertThat(registry.isCurrent(userId, second.getSessionId())).isTrue();
        assertThat(registry.isCurrent(userId, first.getSessionId())).isFalse();
    }

    @Test
    void 동일_사용자에_대한_동시_로그인에서도_최종_세션_하나만_남는다() throws Exception {
        UUID userId = UUID.randomUUID();
        int loginCount = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<LoginSession>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < loginCount; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    LoginSession session = newSession();
                    // 컨트롤러의 로그인 순서(등록 후 이전 세션 무효화)를 재현한다.
                    ActiveSession previous = registry.replace(userId, session);
                    if (previous != null) {
                        previous.session().invalidate();
                    }
                    return session;
                }));
            }
            start.countDown();

            List<LoginSession> loggedInSessions = new ArrayList<>();
            for (Future<LoginSession> future : futures) {
                loggedInSessions.add(future.get(5, TimeUnit.SECONDS));
            }

            String finalSessionId = sessionsByUser().get(userId).sessionId();
            assertThat(usersBySessionId()).hasSize(1);
            assertThat(usersBySessionId()).containsOnlyKeys(finalSessionId);

            long stillValid = loggedInSessions.stream().filter(this::isStillValid).count();
            assertThat(stillValid).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 동시_SSE_교체_후_최종_연결은_정확히_하나다() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);
        int attempts = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<SseReplacement>> futures = new ArrayList<>();
        Set<SseConnection> registeredConnections = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    SseConnection connection = newConnection();
                    registeredConnections.add(connection);
                    return registry.replaceSseIfCurrent(userId, session.getSessionId(), connection);
                }));
            }
            start.countDown();

            for (Future<SseReplacement> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS).registered()).isTrue();
            }

            assertThat(registry.findSse(userId)).isPresent();
            assertThat(registeredConnections).contains(registry.findSse(userId).orElseThrow());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean isStillValid(LoginSession session) {
        try {
            session.isLoggedIn();
            return true;
        } catch (IllegalStateException invalidated) {
            return false;
        }
    }

    private LoginSession newSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        return LoginSession.create(request);
    }

    private SseConnection newConnection() {
        return new SseConnection(UUID.randomUUID().toString(), new SseEmitter());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ActiveSession> sessionsByUser() {
        return (Map<UUID, ActiveSession>) ReflectionTestUtils.getField(registry, "sessionsByUser");
    }

    @SuppressWarnings("unchecked")
    private Map<String, UUID> usersBySessionId() {
        return (Map<String, UUID>) ReflectionTestUtils.getField(registry, "usersBySessionId");
    }
}
