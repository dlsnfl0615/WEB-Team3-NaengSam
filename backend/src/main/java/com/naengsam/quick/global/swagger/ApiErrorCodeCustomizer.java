package com.naengsam.quick.global.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.commonResponse.CommonResponse;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ApiErrorCodes} 가 붙은 API 에 에러 응답 예시를 채워 넣는다.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorCodeCustomizer implements OperationCustomizer {

    private final ObjectMapper objectMapper;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiErrorCodes annotation = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (annotation == null) {
            return operation;
        }

        Map<Integer, List<BaseErrorCode>> byStatus = resolveErrorCodes(annotation).stream()
                .collect(Collectors.groupingBy(code -> code.getStatus().value(),
                        LinkedHashMap::new, Collectors.toList()));

        byStatus.forEach((status, codes) -> operation.getResponses()
                .addApiResponse(String.valueOf(status), toApiResponse(codes)));

        return operation;
    }

    private ApiResponse toApiResponse(List<BaseErrorCode> codes) {
        MediaType mediaType = new MediaType();
        codes.forEach(code -> mediaType.addExamples(code.getCode(), new Example()
                .summary(code.getMessage())
                .value(toExampleValue(code))));

        String description = codes.stream()
                .map(code -> code.getCode() + " " + code.getMessage())
                .collect(Collectors.joining(" / "));

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        mediaType));
    }

    /**
     * 실제 응답 직렬화 결과(Jackson 3)를 swagger 가 쓰는 Jackson 2 트리로 옮긴다. 문자열로 넣으면 Swagger UI 가 이스케이프된 채로 보여주고, 객체로 넣으면 swagger 의
     * NON_NULL 설정에 result 의 null 이 사라진다. JsonNode 로 넣어야 둘 다 피할 수 있다.
     */
    private JsonNode toExampleValue(BaseErrorCode code) {
        String json = objectMapper.writeValueAsString(CommonResponse.onFail(code, null));
        try {
            return Json.mapper().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("에러 응답 예시 파싱에 실패했습니다: " + code.getCode(), e);
        }
    }

    private List<BaseErrorCode> resolveErrorCodes(ApiErrorCodes annotation) {
        BaseErrorCode[] constants = annotation.enumClass().getEnumConstants();
        if (constants == null) {
            throw new IllegalStateException(annotation.enumClass().getName() + " 은 enum 이 아닙니다.");
        }
        if (annotation.codes().length == 0) {
            return List.of(constants);
        }

        Set<String> wanted = Set.of(annotation.codes());
        List<BaseErrorCode> resolved = Arrays.stream(constants)
                .filter(constant -> wanted.contains(((Enum<?>) constant).name()))
                .toList();

        if (resolved.size() != wanted.size()) {
            throw new IllegalStateException(
                    annotation.enumClass().getSimpleName() + " 에 없는 상수가 지정되었습니다: " + wanted);
        }
        return resolved;
    }
}
