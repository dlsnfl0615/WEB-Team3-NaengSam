package com.naengsam.quick.global.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BusinessException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
