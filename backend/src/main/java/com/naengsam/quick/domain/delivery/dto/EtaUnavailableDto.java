package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.global.code.BaseErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * '드리미→픽업지' 경로·배송완료예상시간 계산이 실패했다고 알리는 SSE payload
 * (delivery_eta_unavailable). 드리미와 부르미가 같은 실패를 받으므로 payload도 하나를 공유한다.
 *
 * <p>문구는 실패 원인 에러코드가 이미 갖고 있는 한글 메시지를 그대로 싣는다
 * (예: {@code AddressErrorCode.TOO_FAR_AWAY} → "출발지와 도착지가 너무 멀리 떨어져 있어요.").
 * 화면은 이 메시지를 그대로 노출하고, code는 원인별 분기가 필요해질 때 쓴다.
 */
public record EtaUnavailableDto(
        @Schema(description = "주문 ID", example = "018f1c2e-8a4b-7c3d-9e0f-1a2b3c4d5e6f")
        UUID orderId,

        @Schema(description = "실패 원인 에러코드", example = "ADDRESS_005")
        String code,

        @Schema(description = "화면에 그대로 노출할 실패 이유", example = "출발지와 도착지가 너무 멀리 떨어져 있어요.")
        String message
) {
    public static EtaUnavailableDto from(UUID orderId, BaseErrorCode errorCode) {
        return new EtaUnavailableDto(orderId, errorCode.getCode(), errorCode.getMessage());
    }
}
