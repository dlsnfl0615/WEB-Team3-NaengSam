package com.naengsam.quick.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum GeneralErrorCode implements BaseErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMERR_01", "서버내부에 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMERR_02", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMERR_03", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMERR_04", "지원하지 않는 HTTP 메서드입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
