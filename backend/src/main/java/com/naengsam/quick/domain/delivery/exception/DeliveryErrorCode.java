package com.naengsam.quick.domain.delivery.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum DeliveryErrorCode implements BaseErrorCode {
    NOT_ASSIGNED_DREAMI(HttpStatus.FORBIDDEN, "DELIVERY_001", "접근 권한이 없습니다."),
    PICKUP_PHOTO_MISSING(HttpStatus.BAD_REQUEST, "DELIVERY_002", "픽업 사진을 등록해 주세요."),
    LOCATION_COLLECTION_FAILED(HttpStatus.BAD_REQUEST, "DELIVERY_003", "위치 정보를 확인할 수 없어요."),
    OUTSIDE_PICKUP_RADIUS(HttpStatus.FORBIDDEN, "DELIVERY_004", "픽업 장소에 도착한 후 진행해 주세요."),
    OUTSIDE_DESTINATION_RADIUS(HttpStatus.FORBIDDEN, "DELIVERY_005", "도착 장소에서 완료 처리해 주세요."),
    STEP_ALREADY_VERIFIED(HttpStatus.CONFLICT, "DELIVERY_006", "이미 처리된 단계예요."),
    RECEIPT_CONFIRMATION_TIMEOUT(
            HttpStatus.CONFLICT, "DELIVERY_007", "확인 시간이 지나 자동으로 완료 처리되었어요."),
    CANCELLATION_RESTRICTED_DURING_DELIVERY(
            HttpStatus.CONFLICT, "DELIVERY_008", "지금 취소하면 패널티가 부과될 수 있어요."),
    DAMAGE_REPORT_IN_PROGRESS(HttpStatus.CONFLICT, "DELIVERY_009", "처리 중인 건이라 진행할 수 없어요."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY_010", "존재하지 않는 배달 주문입니다."),
    DELIVERY_COMPLETION_PHOTO_MISSING(
            HttpStatus.BAD_REQUEST, "DELIVERY_011", "배달 완료 인증 사진을 업로드 해주세요."),
    DELIVERY_ALREADY_CANCELLED(HttpStatus.CONFLICT, "DELIVERY_012", "이미 취소된 건에 대한 요청입니다."),
    DELIVERY_ALREADY_COMPLETED(HttpStatus.CONFLICT, "DELIVERY_013", "이미 배달 완료된 주문입니다."),
    PICKUP_NOT_COMPLETED(HttpStatus.CONFLICT, "DELIVERY_014", "픽업 완료 처리에 실패했습니다."),
    CANCELLATION_NOT_COMPLETED(HttpStatus.INTERNAL_SERVER_ERROR, "DELIVERY_015", "배달 취소에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
