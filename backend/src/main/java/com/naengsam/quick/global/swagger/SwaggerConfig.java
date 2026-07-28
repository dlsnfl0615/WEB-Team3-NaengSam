package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.session.LoginUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc(OpenAPI) 전역 설정.
 *
 * <p>{@link LoginUser} 파라미터는 클라이언트 입력이 아니라 {@code LoginUserArgumentResolver} 가 세션에서 주입하는 값이다.
 * 그러나 SpringDoc 스캐너는 핸들러의 모든 파라미터를 검사해 이를 쿼리 파라미터로 문서화하므로, 해당 어노테이션이 붙은 파라미터를
 * 스펙 생성에서 전역 제외한다. static 블록은 설정 빈이 컨텍스트 로딩 중 인스턴스화될 때 실행되어, SpringDoc 스캔 이전에 등록을 보장한다.
 */
@Configuration
public class SwaggerConfig {

    /**
     * 세션 필요 API에 자물쇠를 표시할 때 참조하는 스킴 키. {@link SessionSecurityCustomizer} 가 오퍼레이션별로 이 키를 건다.
     */
    public static final String SESSION_SCHEME = "session";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    /**
     * 세션 쿠키({@code JSESSIONID}) 기반 시큐리티 스킴을 컴포넌트에 등록한다. 자물쇠가 렌더링되려면 참조할 스킴이 정의돼 있어야 한다. 전역
     * securityItem 은 걸지 않는다 — 세션이 필요한 오퍼레이션에만 {@link SessionSecurityCustomizer} 가 개별로 건다.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SESSION_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("세션 쿠키(JSESSIONID). HttpOnly라 값 직접 입력은 동작하지 않으며, "
                                        + "로그인 API 호출 시 브라우저가 자동 전송합니다.")));
    }
}
