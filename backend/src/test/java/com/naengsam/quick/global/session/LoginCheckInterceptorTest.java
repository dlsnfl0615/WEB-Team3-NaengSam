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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.method.HandlerMethod;

/**
 * opt-out 인증: {@code /api/**} 는 기본 로그인 필수이고 {@link PublicApi} 가 붙은 핸들러만 예외로 통과함을 검증한다.
 */
class LoginCheckInterceptorTest {

    private final LoginCheckInterceptor interceptor = new LoginCheckInterceptor();
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
    void 로그인_세션이_있으면_보호_핸들러도_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_USER, UUID.randomUUID());
        request.setSession(session);

        boolean result = interceptor.preHandle(request, response, protectedHandler());

        assertThat(result).isTrue();
    }

    @Test
    void 핸들러가_HandlerMethod가_아니면_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
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
