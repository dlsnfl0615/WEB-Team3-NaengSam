package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.session.PublicApi;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 세션(로그인)이 필요한 오퍼레이션에 세션 시큐리티 요구사항을 걸어 Swagger UI 에 자물쇠를 표시한다.
 * 판정 기준: {@code /api/**} 는 기본적으로 로그인이 필요하므로 자물쇠를 표시하고, {@link PublicApi} 가 메서드/클래스에 붙어 있으면 표시하지 않는다.
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
        boolean isPublic = handlerMethod.getMethodAnnotation(PublicApi.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
        return !isPublic;
    }
}
