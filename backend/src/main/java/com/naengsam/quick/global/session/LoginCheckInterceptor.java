package com.naengsam.quick.global.session;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/**} 핸들러는 기본적으로 로그인 세션을 요구하며, {@link PublicApi} 가 붙은 핸들러(메서드 또는 클래스)만 검사를 건너뛴다.
 * 세션에 {@link SessionConst#LOGIN_USER} 가 없으면 {@link BusinessException} 을 던지고, 이는 {@code GlobalExceptionHandler} 가
 * 공통 포맷의 401 응답으로 변환한다.
 *
 * <p>로그인 attribute가 있는 것만으로는 충분하지 않다 — {@link ActiveSessionRegistry#isCurrent}로 이 servlet 세션이
 * 여전히 계정의 현재 ActiveSession인지까지 확인한다. 다른 곳에서 로그인해 ActiveSession이 교체되면, 이전 servlet
 * 세션에 로그인 attribute가 남아 있어도(아직 만료 전이라면) 더 이상 현재 세션으로 인정하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LoginCheckInterceptor implements HandlerInterceptor {

    private final ActiveSessionRegistry activeSessionRegistry;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 컨트롤러 핸들러가 아닌 요청(정적 리소스 등)은 그대로 통과
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (!requiresLogin(handlerMethod)) {
            return true;
        }

        if (!isCurrentSession(request)) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        return true;
    }

    private boolean isCurrentSession(HttpServletRequest request) {
        return LoginSession.current(request)
                .filter(LoginSession::isLoggedIn)
                .flatMap(session -> session.boormiId()
                        .map(userId -> activeSessionRegistry.isCurrent(userId, session.getSessionId())))
                .orElse(false);
    }

    private boolean requiresLogin(HandlerMethod handlerMethod) {
        boolean isPublic = handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
        return !isPublic;
    }
}
