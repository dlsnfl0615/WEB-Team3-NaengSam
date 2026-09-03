package com.naengsam.quick.domain.address.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 카카오 도보 길찾기 API가 돌려주는 상태 코드를 그대로 매핑한 에러코드. 전부
 * {@link com.naengsam.quick.domain.address.service.KakaoDirectionsService} 에서만 던져진다(HTTP 400).
 * 목록에 없는 상태를 카카오가 반환하면 이 enum이 아니라 공통 {@code EXTERNAL_SERVICE_ERROR} 로 처리된다.
 */
@RequiredArgsConstructor
@Getter
public enum AddressErrorCode implements BaseErrorCode {
    SAME_POINT(HttpStatus.BAD_REQUEST, "ADDRESS_001", "출발지와 도착지를 다르게 설정해 주세요."),
    START_LINK_NOT_FOUND(HttpStatus.BAD_REQUEST, "ADDRESS_002",
            "출발지 주변에서 이동 가능한 도로를 찾을 수 없어요."),
    END_LINK_NOT_FOUND(HttpStatus.BAD_REQUEST, "ADDRESS_003",
            "도착지 주변에서 이동 가능한 도로를 찾을 수 없어요."),
    TOO_MANY_SEARCH_LINK(HttpStatus.BAD_REQUEST, "ADDRESS_004", "경로가 너무 복잡해 탐색할 수 없어요."),
    TOO_FAR_AWAY(HttpStatus.BAD_REQUEST, "ADDRESS_005", "출발지와 도착지가 너무 멀리 떨어져 있어요."),
    ROUTE_RESULT_NOT_FOUND(HttpStatus.BAD_REQUEST, "ADDRESS_006", "이동 가능한 경로를 찾을 수 없어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
