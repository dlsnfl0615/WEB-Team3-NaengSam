package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 배달 진행 중 상태 전이를 트리거하는 API. 각 엔드포인트는 주문(orderId) 단위로 상태를 변경하고, 처리 후 상태 스냅샷(DeliveryStatusResponseDto)을 응답한다.
 * 실제 직렬화는 DeliveryService가 주문 단위 락으로 보장한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/delivery")
@Tag(name = "배달컨트롤러", description = "배달 진행 중 상태 전이(위치 갱신/픽업 완료/취소/배달 완료)를 처리한다")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @Operation(summary = "드리미 위치 갱신",
            description = "드리미가 5~10초마다 호출해 현재 위치를 전달한다. 그 사이 상태 변경이 있었다면 상태 스냅샷으로 응답한다.")
    @PostMapping("/orders/{orderId}/dreami-location")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"LOCATION_COLLECTION_FAILED", "DELIVERY_NOT_FOUND", "DELIVERY_ALREADY_CANCELLED",
                    "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto updateDreamiLocation(
            @PathVariable UUID orderId, @RequestBody(required = false) GeoPoint location) {
        return deliveryService.updateDreamiLocation(orderId, location);
    }

    @Operation(summary = "드리미 픽업 완료", description = "드리미가 픽업을 완료하면 배달중 상태로 전이한다.")
    @PostMapping("/orders/{orderId}/pickup-finish")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"PICKUP_PHOTO_MISSING", "STEP_ALREADY_VERIFIED", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto pickupFinishByDreami(@PathVariable UUID orderId) {
        return deliveryService.pickupFinishByDreami(orderId);
    }

    @Operation(summary = "드리미의 픽업 취소", description = "픽업 과정에서 드리미가 취소한다.")
    @PostMapping("/orders/{orderId}/cancel/dreami")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByDreami(@PathVariable UUID orderId) {
        return deliveryService.cancelByDreami(orderId);
    }

    @Operation(summary = "부르미의 픽업 취소", description = "픽업 과정에서 부르미가 취소한다.")
    @PostMapping("/orders/{orderId}/cancel/boormi")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByBoormi(@PathVariable UUID orderId) {
        return deliveryService.cancelByBoormi(orderId);
    }

    @Operation(summary = "관리자의 픽업 취소", description = "픽업 과정에서 관리자가 취소한다.")
    @PostMapping("/orders/{orderId}/cancel/admin")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByAdmin(@PathVariable UUID orderId) {
        return deliveryService.cancelByAdmin(orderId);
    }

    @Operation(summary = "드리미 배달 완료", description = "드리미가 배달(픽업 아님)을 완료하면 배달 완료 상태로 전이한다.")
    @PostMapping("/orders/{orderId}/finish")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"DELIVERY_NOT_FOUND", "DELIVERY_COMPLETION_PHOTO_MISSING", "DELIVERY_ALREADY_CANCELLED",
                    "DELIVERY_ALREADY_COMPLETED", "PICKUP_NOT_COMPLETED"})
    public DeliveryStatusResponseDto finishDelivery(@PathVariable UUID orderId) {
        return deliveryService.finishDelivery(orderId);
    }
}
