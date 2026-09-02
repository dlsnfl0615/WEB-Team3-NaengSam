package com.naengsam.quick.domain.dreami.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor // Lombok: final 필드(status, code, message) 3개를 받는 생성자를 자동 생성 -> 아래 각 항목의 (HttpStatus, "코드", "메시지") 가 그 생성자 호출
@Getter // Lombok: getStatus(), getCode(), getMessage() 자동 생성
public enum DreamiErrorCode implements BaseErrorCode { // BaseErrorCode: 이 프로젝트가 정의한 공통 인터페이스(전역 예외 처리기가 이 타입으로 다룸)
    NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자 정보를 찾을 수 없습니다."),
    ALREADY_APPROVED(HttpStatus.CONFLICT, "USER_002", "이미 승인된 드리미는 다시 인증 신청을 할 수 없어요."),
    NOT_APPROVED(HttpStatus.FORBIDDEN, "USER_004", "드리미 승인 후 이용할 수 있어요."),
    ALREADY_HAS_ACTIVE_ORDER(HttpStatus.CONFLICT, "USER_006", "수행 중인 주문이 있어 온라인 전환할 수 없어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
