package com.naengsam.quick.global.commonResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.naengsam.quick.global.code.BaseCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.code.GeneralSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class CommonResponse<T> {
    @JsonProperty("isSuccess")
    private final Boolean isSuccess;
    
    @JsonProperty("code")
    private final String code;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("result")
    private final T result;

    public static <T> CommonResponse<T> onFail(BaseErrorCode code, T result) {
        return new CommonResponse<>(false, code.getCode(), code.getMessage(), result);
    }

    public static <T> CommonResponse<T> onSuccess(BaseCode code, T result) {
        return new CommonResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    public static <T> CommonResponse<T> onSuccess(T result) {
        return onSuccess(GeneralSuccessCode.OK, result);
    }
}
