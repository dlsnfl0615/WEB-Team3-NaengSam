package com.naengsam.quick.domain.user.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UserErrorCode implements BaseErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "로그인이 필요합니다."),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, "AUTH_002", "다시 로그인해 주세요."),
    EXPIRED_SESSION(HttpStatus.UNAUTHORIZED, "AUTH_003", "다시 로그인해 주세요."),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "AUTH_004", "접근 권한이 없습니다."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "AUTH_005", "접근 권한이 없습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_006", "아이디 또는 비밀번호가 올바르지 않습니다."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_007", "이미 가입된 계정입니다."),
    //    OFFICE_VERIFICATION_FAILED(HttpStatus.FORBIDDEN, "AUTH_008", "사내 인증 후 이용할 수 있어요."), 순서 보장을 위해 주석만 처리함
    SUSPENDED_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_009", "이용이 제한된 계정입니다. 고객센터로 문의해 주세요."),
    WITHDRAWN_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_010", "이용할 수 없는 계정입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH_011", "인증번호가 올바르지 않습니다."),
    VERIFICATION_CODE_REQUEST_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_012", "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
