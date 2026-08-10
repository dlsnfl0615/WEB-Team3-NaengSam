package com.naengsam.quick.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.service.SmsVerificationService;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.ActiveSessionRegistry;
import com.naengsam.quick.global.session.LoginCheckInterceptor;
import com.naengsam.quick.global.session.LoginSession;
import com.naengsam.quick.global.session.SessionConst;
import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseEmitterRegistry;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.method.HandlerMethod;

/**
 * 사용자당 활성 세션을 하나만 유지하는 로그인/로그아웃 흐름(세션 교체, 이전 SSE 종료, 이전 세션 무효화)을 검증한다.
 */
class UserControllerTest {

    private UserService userService;
    private ActiveSessionRegistry activeSessionRegistry;
    private SseEmitterRegistry sseEmitterRegistry;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        SmsVerificationService smsVerificationService = mock(SmsVerificationService.class);
        activeSessionRegistry = new ActiveSessionRegistry();
        sseEmitterRegistry = mock(SseEmitterRegistry.class);
        controller = new UserController(userService, smsVerificationService, activeSessionRegistry,
                sseEmitterRegistry);
    }

    @Test
    void 첫_로그인은_활성_세션_하나를_등록하고_SSE_종료는_없다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);

        controller.login(loginRequest(), new MockHttpServletRequest());

        verify(sseEmitterRegistry, never()).disconnectAll(any(), any());
    }

    @Test
    void 두번째_로그인은_첫_세션을_무효화한다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        controller.login(loginRequest(), firstRequest);
        LoginSession firstSession = LoginSession.current(firstRequest).orElseThrow();

        controller.login(loginRequest(), new MockHttpServletRequest());

        assertThat(isStillValid(firstSession)).isFalse();
    }

    @Test
    void 두번째_로그인은_이전_사용자의_SSE를_전체_종료한다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        controller.login(loginRequest(), new MockHttpServletRequest());

        controller.login(loginRequest(), new MockHttpServletRequest());

        verify(sseEmitterRegistry).disconnectAll(userId, SseCloseReason.REPLACED_BY_LOGIN);
    }

    @Test
    void 다른_사용자의_세션과_SSE는_영향받지_않는다() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        given(userService.login(any())).willReturn(userA, userB);
        MockHttpServletRequest requestA = new MockHttpServletRequest();
        controller.login(loginRequest(), requestA);
        LoginSession sessionA = LoginSession.current(requestA).orElseThrow();

        controller.login(loginRequest(), new MockHttpServletRequest());

        assertThat(isStillValid(sessionA)).isTrue();
        verify(sseEmitterRegistry, never()).disconnectAll(eq(userA), any());
    }

    @Test
    void 동일_사용자의_동시_로그인에서도_최종_세션_하나만_유지된다() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        int loginCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<HttpSession>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < loginCount; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    // 로그인 직후 다른 스레드가 곧바로 이 세션을 무효화할 수 있으므로, request 를 통해 다시 조회하지 않고
                    // getSession(true) 시점에 생성된 세션 객체를 직접 붙잡아 둔다.
                    CapturingRequest request = new CapturingRequest();
                    controller.login(loginRequest(), request);
                    return request.created();
                }));
            }
            start.countDown();

            List<HttpSession> sessions = new ArrayList<>();
            for (Future<HttpSession> future : futures) {
                sessions.add(future.get(5, TimeUnit.SECONDS));
            }

            long stillValid = sessions.stream().filter(this::isStillValid).count();
            assertThat(stillValid).isEqualTo(1);
            verify(sseEmitterRegistry, times(loginCount - 1)).disconnectAll(userId, SseCloseReason.REPLACED_BY_LOGIN);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 로그아웃하면_해당_사용자의_모든_emitter를_종료한다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        MockHttpServletRequest loginRequest = new MockHttpServletRequest();
        controller.login(loginRequest(), loginRequest);

        controller.logout(loginRequest);

        verify(sseEmitterRegistry).disconnectAll(userId, SseCloseReason.LOGOUT);
    }

    @Test
    void 로그아웃하면_세션이_무효화된다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        MockHttpServletRequest loginRequest = new MockHttpServletRequest();
        controller.login(loginRequest(), loginRequest);
        LoginSession session = LoginSession.current(loginRequest).orElseThrow();

        controller.logout(loginRequest);

        assertThat(isStillValid(session)).isFalse();
    }

    @Test
    void 로그아웃해도_다른_사용자의_세션과_SSE는_영향받지_않는다() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        given(userService.login(any())).willReturn(userA, userB);
        MockHttpServletRequest requestA = new MockHttpServletRequest();
        controller.login(loginRequest(), requestA);
        MockHttpServletRequest requestB = new MockHttpServletRequest();
        controller.login(loginRequest(), requestB);
        LoginSession sessionB = LoginSession.current(requestB).orElseThrow();

        controller.logout(requestA);

        assertThat(isStillValid(sessionB)).isTrue();
        verify(sseEmitterRegistry, never()).disconnectAll(eq(userB), any());
    }

    @Test
    void 이전_세션으로_들어온_요청은_401이_된다() {
        UUID userId = UUID.randomUUID();
        given(userService.login(any())).willReturn(userId);
        controller.login(loginRequest(), new MockHttpServletRequest());

        controller.login(loginRequest(), new MockHttpServletRequest());

        // 이전 세션은 이미 무효화·제거됐으므로, 그 쿠키로 들어오는 다음 요청은 서버에 세션이 없는 것과 같다.
        MockHttpServletRequest staleRequest = new MockHttpServletRequest();
        LoginCheckInterceptor interceptor = new LoginCheckInterceptor();

        Throwable thrown = catchThrowable(() ->
                interceptor.preHandle(staleRequest, new MockHttpServletResponse(), protectedHandler()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    void 로그인_로그에는_세션ID가_기록되지_않는다() {
        Logger registryLogger = (Logger) LoggerFactory.getLogger(ActiveSessionRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        registryLogger.addAppender(appender);
        registryLogger.setLevel(Level.DEBUG);

        try {
            UUID userId = UUID.randomUUID();
            given(userService.login(any())).willReturn(userId);
            // MockHttpSession의 기본 id는 짧은 순번(예: "1")이라 로그 메시지 속 다른 숫자와 우연히 겹칠 수 있으므로,
            // 충돌 가능성이 없는 UUID 기반 id를 직접 부여한다.
            UniqueIdRequest firstRequest = new UniqueIdRequest();
            controller.login(loginRequest(), firstRequest);
            String firstSessionId = LoginSession.current(firstRequest).orElseThrow().getSessionId();

            controller.login(loginRequest(), new MockHttpServletRequest());

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(firstSessionId));
        } finally {
            registryLogger.detachAppender(appender);
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

    private boolean isStillValid(HttpSession session) {
        try {
            session.getAttribute(SessionConst.LOGIN_USER);
            return true;
        } catch (IllegalStateException invalidated) {
            return false;
        }
    }

    private LoginRequest loginRequest() {
        return new LoginRequest("user@test.com", "password1");
    }

    private HandlerMethod protectedHandler() {
        try {
            ProtectedController bean = new ProtectedController();
            return new HandlerMethod(bean, bean.getClass().getMethod("handle"));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static class ProtectedController {
        public void handle() {
        }
    }

    /**
     * 로그인 직후 다른 스레드의 세션 교체가 request 의 세션 참조를 지워버리기 전에, 생성된 세션 객체를 붙잡아 둔다.
     */
    static class CapturingRequest extends MockHttpServletRequest {
        private volatile HttpSession created;

        @Override
        public HttpSession getSession(boolean create) {
            HttpSession session = super.getSession(create);
            if (session != null) {
                created = session;
            }
            return session;
        }

        HttpSession created() {
            return created;
        }
    }

    /**
     * MockHttpSession의 기본 id(짧은 순번)는 다른 로그 값과 우연히 겹칠 수 있어, 세션ID 미기록 검증에는 충돌 가능성이
     * 없는 UUID 기반 id를 직접 부여한 세션을 쓴다.
     */
    static class UniqueIdRequest extends MockHttpServletRequest {
        @Override
        public HttpSession getSession(boolean create) {
            HttpSession existing = super.getSession(false);
            if (existing != null || !create) {
                return existing;
            }
            MockHttpSession session = new MockHttpSession(getServletContext(), UUID.randomUUID().toString());
            setSession(session);
            return session;
        }
    }
}
