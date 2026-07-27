package com.naengsam.quick.domain.user.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UserErrorCode implements BaseErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "로그인이 필요합니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_01", "존재하지 않는 사용자"),
    ALREADY_EXIST(HttpStatus.CONFLICT, "USER_02", "이미 드리미로 등록된 사용자"),
    INVALID_USER(HttpStatus.BAD_REQUEST, "USER-ERR-02", "접근 할 수 없는 계정입니다."),
    INCORRECT_PASSWORD(HttpStatus.FORBIDDEN, "USER-ERR-03", "비밀번호가 일치하지 않습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

