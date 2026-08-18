package com.naengsam.quick.global.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * opt-out 인증: {@code /api/**} 는 기본 로그인 필수이고 {@link PublicApi} 가 붙은 핸들러만 예외로 통과함을 검증한다.
 * 로그인 attribute가 있는 것만으로는 부족하고, {@link ActiveSessionRegistry}가 인정하는 현재 세션이어야 통과한다.
 */
class LoginCheckInterceptorTest {

    private final ActiveSessionRegistry activeSessionRegistry = new ActiveSessionRegistry();
    private final LoginCheckInterceptor interceptor = new LoginCheckInterceptor(activeSessionRegistry);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void 어노테이션_없는_핸들러는_비로그인이면_UNAUTHORIZED() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Throwable thrown = catchThrowable(() -> interceptor.preHandle(request, response, protectedHandler()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    void PublicApi_메서드는_비로그인이어도_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = interceptor.preHandle(request, response, publicMethodHandler());

        assertThat(result).isTrue();
    }

    @Test
    void PublicApi_클래스는_비로그인이어도_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = interceptor.preHandle(request, response, publicClassHandler());

        assertThat(result).isTrue();
    }

    @Test
    void 현재_ActiveSession이면_보호_핸들러도_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID userId = UUID.randomUUID();
        loginAsCurrentSession(request, userId);

        boolean result = interceptor.preHandle(request, response, protectedHandler());

        assertThat(result).isTrue();
    }

    @Test
    void 로그인_attribute가_있어도_다른_곳에서_로그인해_교체됐으면_UNAUTHORIZED() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID userId = UUID.randomUUID();
        loginAsCurrentSession(request, userId);
        // 다른 곳에서 다시 로그인해 ActiveSession이 교체된다. 이 request의 servlet 세션 attribute는 그대로 남아 있다.
        activeSessionRegistry.replace(userId, LoginSession.create(new MockHttpServletRequest()));

        Throwable thrown = catchThrowable(() -> interceptor.preHandle(request, response, protectedHandler()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    void 핸들러가_HandlerMethod가_아니면_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    /** 실제 로그인 플로우처럼 이 request의 servlet 세션을 만들고 ActiveSessionRegistry에도 현재 세션으로 등록한다. */
    private void loginAsCurrentSession(MockHttpServletRequest request, UUID userId) {
        LoginSession session = LoginSession.create(request);
        session.login(userId);
        activeSessionRegistry.replace(userId, session);
    }

    private HandlerMethod protectedHandler() {
        return handlerOf(new ProtectedController(), "handle");
    }

    private HandlerMethod publicMethodHandler() {
        return handlerOf(new ProtectedController(), "publicHandle");
    }

    private HandlerMethod publicClassHandler() {
        return handlerOf(new PublicController(), "handle");
    }

    private HandlerMethod handlerOf(Object bean, String methodName) {
        try {
            return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    static class ProtectedController {
        public void handle() {
        }

        @PublicApi
        public void publicHandle() {
        }
    }

    @PublicApi
    static class PublicController {
        public void handle() {
        }
    }
}
