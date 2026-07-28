package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.session.LoginRequired;
import com.naengsam.quick.global.session.LoginUser;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Arrays;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 세션(로그인)이 필요한 오퍼레이션에만 세션 시큐리티 요구사항을 걸어 Swagger UI 에 자물쇠를 표시한다.
 * 판정 기준: {@link LoginUser} 파라미터가 있거나 {@link LoginRequired} 가 메서드/클래스에 붙어 있으면 세션 필요로 본다.
 */
@Component
public class SessionSecurityCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (requiresSession(handlerMethod)) {
            operation.addSecurityItem(new SecurityRequirement().addList(SwaggerConfig.SESSION_SCHEME));
        }
        return operation;
    }

    private boolean requiresSession(HandlerMethod handlerMethod) {
        boolean hasLoginUserParam = Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(LoginUser.class));
        boolean loginRequired = handlerMethod.getMethodAnnotation(LoginRequired.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
        return hasLoginUserParam || loginRequired;
    }
}
