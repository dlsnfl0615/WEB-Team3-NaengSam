package com.naengsam.quick.global.commonResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

/**
 * 컨트롤러가 반환한 값을 {@link CommonResponse} 로 감싼다. 컨트롤러는 DTO 만 반환하면 되고, 공통 포맷은 여기서 씌운다.
 *
 * <p>basePackages 를 반드시 도메인으로 한정해야 한다. 범위를 열어두면 springdoc 의
 * {@code OpenApiWebMvcResource} 도 {@code @RestController} 라서 {@code /v3/api-docs} 응답까지 감싸버려 Swagger UI 가 통째로 깨진다.
 */
@RestControllerAdvice(basePackages = "com.naengsam.quick.domain")
@RequiredArgsConstructor
public class CommonResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 이미 감싼 응답, 파일·바이너리 응답은 그대로 내보낸다
        if (body instanceof CommonResponse || body instanceof byte[] || body instanceof Resource) {
            return body;
        }

        // String 은 StringHttpMessageConverter 가 쓰기 때문에 객체를 돌려주면 캐스팅에서 터진다
        if (body instanceof String) {
            return objectMapper.writeValueAsString(CommonResponse.onSuccess(body));
        }

        // body 가 null 이어도(반환값 없는 성공) 빈 바디 대신 result=null 봉투로 내려보낸다.
        return CommonResponse.onSuccess(body);
    }
}
