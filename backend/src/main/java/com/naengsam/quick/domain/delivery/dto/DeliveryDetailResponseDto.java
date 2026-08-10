package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.order.entity.Orders;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 배달 추적 화면용 상세 응답. 출발지·도착지 좌표는 Orders에서, 상태·현재 드리미 위치는 Delivery에서 조합한다.
 * currentLocation은 드리미의 최신 위치 스냅샷이다(아직 갱신 전이면 null).
 */
public record DeliveryDetailResponseDto(
        @Schema(description = "주문 ID", example = "018f1c2e-8a4b-7c3d-9e0f-1a2b3c4d5e6f")
        UUID orderId,

        @Schema(description = "배달 상태", example = "DELIVERING")
        DeliveryCd status,

        @Schema(description = "드리미의 최신 위치(아직 갱신 전이면 null)")
        DeliveryLocationDto currentLocation,

        @Schema(description = "출발지 위도", example = "37.49794500")
        BigDecimal originLatitude,

        @Schema(description = "출발지 경도", example = "127.02758300")
        BigDecimal originLongitude,

        @Schema(description = "출발지 기본주소(도로명)", example = "서울시 강남구 테헤란로 123")
        String originAddressLine1,

        @Schema(description = "도착지 위도", example = "37.49123400")
        BigDecimal destinationLatitude,

        @Schema(description = "도착지 경도", example = "127.03456700")
        BigDecimal destinationLongitude,

        @Schema(description = "도착지 기본주소(도로명)", example = "서울시 서초구 서초대로 45")
        String destinationAddressLine1,

        @Schema(description = "물건 이름", example = "서류봉투")
        String itemName,

        @Schema(description = "픽업지→도착지 카카오 추천 도보 경로 좌표 목록(픽업 후 지도 폴리라인용). 경로 정보가 없으면 빈 배열")
        List<RoutePointDto> routePath,

        @Schema(description = "드리미 위치→픽업지 카카오 추천 도보 경로 좌표 목록(픽업 전 지도 폴리라인용). 아직 계산 전이면 빈 배열")
        List<RoutePointDto> deliveryRoutePath,

        @Schema(description = "배송완료예상시간(드리미→픽업지 소요 + 주문 delivery_eta). 아직 계산 전이면 null")
        LocalDateTime estimatedCompletionTime
) {
    public static DeliveryDetailResponseDto from(Delivery delivery, Orders order,
            List<RoutePointDto> routePath, List<RoutePointDto> deliveryRoutePath) {
        DeliveryLocationDto currentLocation = delivery.getCurrentLatitude() == null
                ? null
                : new DeliveryLocationDto(
                        delivery.getCurrentLatitude(),
                        delivery.getCurrentLongitude());

        return new DeliveryDetailResponseDto(
                order.getOrderId(),
                delivery.getDeliveryCd(),
                currentLocation,
                order.getOriginLatitude(),
                order.getOriginLongitude(),
                order.getOriginAddressLine1(),
                order.getDestinationLatitude(),
                order.getDestinationLongitude(),
                order.getDestinationAddressLine1(),
                order.getItemName(),
                routePath,
                deliveryRoutePath,
                delivery.getEstimatedCompletionDtm());
    }
}
