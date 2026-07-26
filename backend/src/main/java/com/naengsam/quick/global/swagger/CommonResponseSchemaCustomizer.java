package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.code.GeneralSuccessCode;
import com.naengsam.quick.global.commonResponse.CommonResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 성공 응답 스키마를 {@link CommonResponse} 모양으로 감싼다. 컨트롤러는 DTO 만 반환하지만 실제 응답은 CommonResponseAdvice 가 감싸므로, 그대로 두면 문서가 실제 응답과
 * 어긋난다.
 */
@Component
public class CommonResponseSchemaCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (CommonResponse.class.isAssignableFrom(handlerMethod.getReturnType().getParameterType())) {
            return operation;
        }

        operation.getResponses().forEach((status, apiResponse) -> {
            if (!status.startsWith("2") || apiResponse.getContent() == null) {
                return;
            }
            apiResponse.getContent().values()
                    .forEach(mediaType -> mediaType.setSchema(wrap(mediaType.getSchema())));
        });

        return operation;
    }

    private Schema<?> wrap(Schema<?> result) {
        return new ObjectSchema()
                .addProperty("isSuccess", new BooleanSchema().example(true))
                .addProperty("code", new StringSchema().example(GeneralSuccessCode.OK.getCode()))
                .addProperty("message", new StringSchema().example(GeneralSuccessCode.OK.getMessage()))
                .addProperty("result", result);
    }
}
