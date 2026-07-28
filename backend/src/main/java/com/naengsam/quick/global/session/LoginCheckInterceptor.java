package com.naengsam.quick.global.session;

import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link LoginRequired} 가 붙은 핸들러(메서드 또는 클래스)에 대해 로그인 세션을 검사한다. 세션에 {@link SessionConst#LOGIN_USER} 가 없으면
 * {@link BusinessException} 을 던지고, 이는 {@code GlobalExceptionHandler} 가 공통 포맷의 401 응답으로 변환한다.
 */
@Component
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 컨트롤러 핸들러가 아닌 요청(정적 리소스 등)은 그대로 통과
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (!requiresLogin(handlerMethod)) {
            return true;
        }

        boolean loggedIn = LoginSession.current(request)
                .map(LoginSession::isLoggedIn)
                .orElse(false);
        if (!loggedIn) {
            throw new BusinessException(UserErrorCode.UNAUTHORIZED);
        }

        return true;
    }

    private boolean requiresLogin(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(LoginRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
    }
}
