package com.naengsam.quick.domain.dreami.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum DreamiErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "드리미 정보를 찾을 수 없습니다."),
    NOT_APPROVED(HttpStatus.FORBIDDEN, "USER_004", "드리미 승인 후 이용할 수 있어요."),
    ALREADY_HAS_ACTIVE_ORDER(HttpStatus.CONFLICT, "USER_006", "수행 중인 주문이 있어 온라인 전환할 수 없어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
