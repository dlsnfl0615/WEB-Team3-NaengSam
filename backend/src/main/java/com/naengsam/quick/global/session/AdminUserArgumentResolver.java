package com.naengsam.quick.global.session;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @AdminUser UUID adminId} 파라미터에 세션에 저장된 현재 사용자 식별자를 주입한다. 세션이 없으면
 * {@link LoginUserArgumentResolver}와 동일하게 UNAUTHORIZED, 로그인은 했지만 {@link Boormi#isAdmin()}이 false면
 * FORBIDDEN_ROLE 을 던진다 — 즉 {@link AdminUser} 사용은 관리자 로그인 필수를 의미한다.
 */
@Component
@RequiredArgsConstructor
public class AdminUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final BoormiRepository boormiRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AdminUser.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        UUID boormiId = LoginSession.current(request)
                .flatMap(LoginSession::boormiId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));

        Boormi boormi = boormiRepository.findById(boormiId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));
        if (!boormi.isAdmin()) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN_ROLE);
        }
        return boormiId;
    }
}
