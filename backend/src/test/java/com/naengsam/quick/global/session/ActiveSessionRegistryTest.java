package com.naengsam.quick.global.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 사용자당 활성 세션을 하나만 유지하는 {@link ActiveSessionRegistry}의 교체/제거 동작을 검증한다.
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
    void removeIfCurrent은_현재_세션이면_userId를_반환하고_제거한다() {
        UUID userId = UUID.randomUUID();
        LoginSession session = newSession();
        registry.replace(userId, session);

        Optional<UUID> removed = registry.removeIfCurrent(session.getSessionId());

        assertThat(removed).contains(userId);
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

        Optional<UUID> removed = registry.removeIfCurrent(first.getSessionId());

        assertThat(removed).isEmpty();
        assertThat(sessionsByUser().get(userId).session()).isSameAs(second);
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

    @SuppressWarnings("unchecked")
    private Map<UUID, ActiveSession> sessionsByUser() {
        return (Map<UUID, ActiveSession>) ReflectionTestUtils.getField(registry, "sessionsByUser");
    }

    @SuppressWarnings("unchecked")
    private Map<String, UUID> usersBySessionId() {
        return (Map<String, UUID>) ReflectionTestUtils.getField(registry, "usersBySessionId");
    }
}
