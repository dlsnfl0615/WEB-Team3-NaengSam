package com.naengsam.quick.domain.user.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USERERR-01", "유저가 존재하지 않습니다."),
    INVALID_PASSWORD(HttpStatus.FORBIDDEN, "USER-ERR-02", "비밀번호가 일치하지 않습니다"),
    INCORRECT_PASSWORD(HttpStatus.FORBIDDEN, "USER-ERR-03", "비밀번호가 일치하지 않습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

