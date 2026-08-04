package com.naengsam.quick.domain.matching.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum MatchingErrorCode implements BaseErrorCode {
    /**
     * 현재 커밋에서는 가장 기본적인 에러코드만 구현. 이후 커밋에서 수정 예정.
     */
    NOT_OFFER_OWNER(HttpStatus.FORBIDDEN, "MATCHING_001", "본인에게 온 제안만 처리할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
