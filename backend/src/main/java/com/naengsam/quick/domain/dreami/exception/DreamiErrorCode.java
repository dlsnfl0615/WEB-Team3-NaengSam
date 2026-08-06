package com.naengsam.quick.domain.dreami.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum DreamiErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "드리미 정보를 찾을 수 없습니다."),
    ALREADY_APPROVED(HttpStatus.CONFLICT, "USER_002", "이미 승인된 드리미는 다시 신청할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
