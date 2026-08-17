package com.naengsam.quick.global.config;

import com.naengsam.quick.global.session.AdminUserArgumentResolver;
import com.naengsam.quick.global.session.LoginCheckInterceptor;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 세션 기반 인증을 위한 인터셉터와 ArgumentResolver 를 등록한다.
 * ({@code @EnableWebMvc} 는 붙이지 않는다 — Spring Boot 자동설정을 유지하기 위함.)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginCheckInterceptor loginCheckInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AdminUserArgumentResolver adminUserArgumentResolver;

    /** 콤마 구분 허용 오리진 목록. 운영은 {@code CORS_ALLOWED_ORIGINS} 환경변수로 주입. */
    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginCheckInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/swagger-ui/**", "/v3/api-docs/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
        resolvers.add(adminUserArgumentResolver);
    }
}
