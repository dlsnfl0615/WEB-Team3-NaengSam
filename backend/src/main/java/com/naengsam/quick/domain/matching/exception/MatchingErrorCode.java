package com.naengsam.quick.domain.matching.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 매칭(요청 수락·확정) 도메인에서 발생하는 비즈니스 예외 코드.
 */
@RequiredArgsConstructor
@Getter
public enum MatchingErrorCode implements BaseErrorCode {
    ALREADY_ACCEPTED_BY_OTHER(HttpStatus.CONFLICT, "MATCH_001", "이미 다른 드리미가 수락했어요."),
    CANNOT_ACCEPT_OWN_REQUEST(HttpStatus.FORBIDDEN, "MATCH_002", "본인 요청은 수락할 수 없어요."),
    ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "MATCH_003", "진행 중인 배송을 먼저 완료해 주세요."),
    NOT_ACCEPTABLE_STATUS(HttpStatus.CONFLICT, "MATCH_004", "지금은 수락할 수 없는 요청이에요."),
    MATCHING_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_005", "매칭 정보를 찾을 수 없습니다."),
    BOORMI_CONFIRMATION_TIMEOUT(HttpStatus.CONFLICT, "MATCH_006", "확정 시간이 지나 매칭이 취소되었어요."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "MATCH_007", "잠시 후 다시 시도해 주세요."),
    NOT_OFFER_OWNER(HttpStatus.FORBIDDEN, "MATCH_008", "본인에게 온 제안만 처리할 수 있습니다."),
    OFFER_EXPIRED(HttpStatus.CONFLICT, "MATCH_009", "이미 만료된 제안이에요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
