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
 *
 * 픽업사진은 부르미가 버튼을 눌렀을 때 {@code GET /api/v1/delivery/orders/{orderId}/pickup-photo}로 그때 조회한다.
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

        @Schema(description = "부르미가 작성한 요청 사항. 없으면 null", example = "문 앞에 놓아주세요", nullable = true)
        String deliveryRequest,

        @Schema(description = "부르미가 등록한 물품 사진 다운로드 URL. 사진이 없거나 조회 실패 시 null",
                example = "https://s3.ap-northeast-2.amazonaws.com/...", nullable = true)
        String itemPhotoUrl,

        @Schema(description = "픽업지→도착지 카카오 추천 도보 경로 좌표 목록(픽업 후 지도 폴리라인용). 경로 정보가 없으면 빈 배열")
        List<RoutePointDto> routePath,

        @Schema(description = "드리미 위치→픽업지 카카오 추천 도보 경로 좌표 목록(픽업 전 지도 폴리라인용). 아직 계산 전이면 빈 배열")
        List<RoutePointDto> deliveryRoutePath,

        @Schema(description = "배송완료예상시간(드리미→픽업지 소요 + 주문 delivery_eta). 아직 계산 전이면 null")
        LocalDateTime estimatedCompletionTime,

        @Schema(description = "드리미 위치가 끊긴 상태인지(true면 화면에 안내 필요)", example = "false")
        boolean dreamiOffline,

        @Schema(description = "마지막으로 드리미 위치를 받은 뒤 흐른 시간(초). 위치를 한 번도 못 받았으면 null",
                example = "7")
        Long secondsSinceLastLocation
) {
    public static DeliveryDetailResponseDto from(Delivery delivery, Orders order,
            String itemPhotoUrl,
            List<RoutePointDto> routePath, List<RoutePointDto> deliveryRoutePath,
            boolean dreamiOffline, Long secondsSinceLastLocation) {
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
                order.getDeliveryRequest(),
                itemPhotoUrl,
                routePath,
                deliveryRoutePath,
                delivery.getEstimatedCompletionDtm(),
                dreamiOffline,
                secondsSinceLastLocation);
    }
}
