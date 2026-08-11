package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.order.entity.Orders;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * 배달 완료 화면용 요약 응답. 추적 화면(DeliveryDetailResponseDto)과 달리 위치·경로 정보는 없고,
 * 완료 후에만 의미 있는 정산·담당 드리미·소요시간을 담는다.
 */
public record DeliveryCompletionDto(
        @Schema(description = "주문 ID", example = "018f1c2e-8a4b-7c3d-9e0f-1a2b3c4d5e6f")
        UUID orderId,

        @Schema(description = "물건 이름", example = "서류봉투")
        String itemName,

        @Schema(description = "물건 카테고리", example = "DOCUMENT")
        ItemCd itemCd,

        @Schema(description = "담당 드리미 이름", example = "김드림")
        String dreamiName,

        @Schema(description = "담당 드리미 평균 평점", example = "4.80")
        BigDecimal dreamiAvgScore,

        @Schema(description = "담당 부르미 이름", example = "이부름")
        String boormiName,

        @Schema(description = "결제 금액", example = "8000")
        Long deliveryAmount,

        @Schema(description = "배달 소요 시간(분). 픽업 전이거나 아직 완료 전이면 null", example = "8")
        Long durationMinutes,

        @Schema(description = "배송 완료 인증 사진 다운로드 URL. 인증 사진이 없으면 null",
                example = "https://s3.ap-northeast-2.amazonaws.com/...")
        String deliveryPhotoUrl
) {
    public static DeliveryCompletionDto from(Delivery delivery, Orders order, String dreamiName,
            BigDecimal dreamiAvgScore, String boormiName, String deliveryPhotoUrl) {
        Long durationMinutes = (delivery.getDeliveryStartDtm() == null || delivery.getDeliveryEndDtm() == null)
                ? null
                : Duration.between(delivery.getDeliveryStartDtm(), delivery.getDeliveryEndDtm()).toMinutes();

        return new DeliveryCompletionDto(
                order.getOrderId(),
                order.getItemName(),
                order.getItemCd(),
                dreamiName,
                dreamiAvgScore,
                boormiName,
                order.getDeliveryAmount(),
                durationMinutes,
                deliveryPhotoUrl);
    }
}
