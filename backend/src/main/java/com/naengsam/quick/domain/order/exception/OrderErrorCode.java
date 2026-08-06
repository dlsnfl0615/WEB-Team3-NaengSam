package com.naengsam.quick.domain.order.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum OrderErrorCode implements BaseErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "요청 정보를 찾을 수 없습니다."),
    NOT_ORDER_OWNER(HttpStatus.FORBIDDEN, "ORDER_002", "접근 권한이 없습니다."),
    CANNOT_MODIFY_AFTER_MATCHED(HttpStatus.CONFLICT, "ORDER_003", "이미 매칭된 요청은 수정할 수 없어요."),
    CANNOT_CANCEL_AFTER_PICKUP(HttpStatus.CONFLICT, "ORDER_004", "이미 진행 중이라 취소할 수 없어요."),
    SAME_ORIGIN_DESTINATION(HttpStatus.BAD_REQUEST, "ORDER_005", "출발지와 도착지를 다르게 설정해 주세요."),
    OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "ORDER_006", "서비스 가능 지역이 아니에요."),
    INVALID_DESIRED_TIME(HttpStatus.BAD_REQUEST, "ORDER_007", "시간을 다시 선택해 주세요."),
    OUT_OF_OPERATING_HOURS(HttpStatus.BAD_REQUEST, "ORDER_008", "지금은 이용할 수 없는 시간이에요."),
    TOO_MANY_ACTIVE_ORDERS(HttpStatus.CONFLICT, "ORDER_009", "진행 중인 요청을 먼저 완료해 주세요."),
    PROHIBITED_ITEM(HttpStatus.BAD_REQUEST, "ORDER_010", "해당 품목은 요청할 수 없어요."),
    INAPPROPRIATE_EXPRESSION(HttpStatus.BAD_REQUEST, "ORDER_011", "사용할 수 없는 표현이 포함되어 있어요."),
    MISSING_REQUIRED_INFO(HttpStatus.BAD_REQUEST, "ORDER_012", "필수 정보를 모두 입력해 주세요."),
    ORDER_EXPIRED(HttpStatus.GONE, "ORDER_013", "요청 시간이 만료되었어요. 다시 등록해 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "ORDER_014", "잘못된 커서입니다."),
    NO_DREAMI_TO_CONFIRM(HttpStatus.CONFLICT, "ORDER_015", "확정할 드리미가 없어요."),
    INVALID_DREAMI_CONFIRMATION(HttpStatus.CONFLICT, "ORDER_016", "확정할 수 없는 상태예요."),
    CANNOT_CANCEL(HttpStatus.CONFLICT, "ORDER_017", "취소할 수 없는 상태예요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
