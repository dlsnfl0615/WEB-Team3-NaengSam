package com.naengsam.quick.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum GeneralErrorCode implements BaseErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값을 다시 확인해 주세요."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_002", "입력값을 다시 확인해 주세요."),
    MISSING_REQUEST_VALUE(HttpStatus.BAD_REQUEST, "COMMON_003", "요청 정보가 올바르지 않습니다."),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON_004", "요청 정보가 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_005", "요청하신 정보를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "요청하신 정보를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_007", "요청 정보가 올바르지 않습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_008", "다른 곳에서 변경된 내용이 있어요. 새로고침 후 다시 시도해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_009", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_010", "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "COMMON_011", "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "COMMON_012", "응답이 지연되고 있어요. 잠시 후 다시 시도해 주세요."),
    LOCATION_PERMISSION_DENIED(HttpStatus.BAD_REQUEST, "COMMON_013", "위치 권한을 허용해야 이용할 수 있어요."),
    NOTIFICATION_PERMISSION_DENIED(HttpStatus.BAD_REQUEST, "COMMON_014", "알림 권한을 허용해야 이용할 수 있어요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "로그인이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
