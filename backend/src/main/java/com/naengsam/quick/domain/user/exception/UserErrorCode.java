package com.naengsam.quick.domain.user.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자 정보를 찾을 수 없습니다."),
    DREAMI_ALREADY_REGISTERED(HttpStatus.CONFLICT, "USER_002", "이미 드리미로 등록되어 있어요."),
    DREAMI_NOT_REGISTERED(HttpStatus.FORBIDDEN, "USER_003", "드리미 등록 후 이용할 수 있어요."),
    DREAMI_NOT_APPROVED(HttpStatus.FORBIDDEN, "USER_004", "드리미 승인 후 이용할 수 있어요."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_005", "이미 사용 중인 닉네임이에요."),
    CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER(HttpStatus.CONFLICT, "USER_006", "수행 중인 주문이 있어 전환할 수 없어요."),
    CANNOT_CHANGE_ROLE_WHILE_MATCHING(HttpStatus.CONFLICT, "USER_007",
            "매칭 대기 중에는 전환할 수 없어요. 먼저 오프라인으로 전환해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
