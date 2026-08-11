package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.delivery.dto.DeliveryDetailResponseDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryPhotoRequest;
import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationResponseDto;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    // 배달 추적 페이지를 로드할 때 먼저 호출해야하는 함수
    @Operation(summary = "배달 상세 조회", description = "추적 화면용. 출발지·도착지 좌표와 현재 드리미 위치를 반환한다.")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class, codes = {"DELIVERY_NOT_FOUND"})
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"NOT_RESOURCE_OWNER"})
    @GetMapping("/orders/{orderId}")
    public DeliveryDetailResponseDto getDeliveryDetail(
            @PathVariable UUID orderId, @LoginUser UUID userId) {
        return deliveryService.getDeliveryDetail(orderId, userId);
    }

    @Operation(summary = "드리미 위치 갱신",
            description = "드리미가 5~10초마다 호출해 현재 위치를 전달한다. 첫 위치가 도착하면 서버가 계산한 '드리미→픽업지' 경로와 "
                    + "배송완료예상시간을 함께 응답한다(아직 계산 전이면 빈 목록/null). 상태 변경은 SSE로 전달된다. "
                    + "이미 취소/완료된 주문이면 폴링 중단 신호로 에러를 응답한다.")
    @PostMapping("/orders/{orderId}/dreami-location")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"LOCATION_COLLECTION_FAILED", "DELIVERY_NOT_FOUND", "DELIVERY_ALREADY_CANCELLED",
                    "DELIVERY_ALREADY_COMPLETED"})
    public DreamiLocationResponseDto updateDreamiLocation(
            @PathVariable UUID orderId, @RequestBody(required = false) DreamiLocationRequest location) {
        return deliveryService.updateDreamiLocation(orderId, location);
    }

    @Operation(summary = "드리미 픽업 완료",
            description = "드리미가 픽업을 완료하면 배달중 상태로 전이한다. 업로드한 픽업 인증 사진의 key를 함께 보낸다.")
    @PostMapping("/orders/{orderId}/pickup-finish")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"NOT_ASSIGNED_DREAMI", "STEP_ALREADY_VERIFIED", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    @ApiErrorCodes(enumClass = UploadErrorCode.class,
            codes = {"FILE_NOT_FOUND", "KEY_OWNER_MISMATCH", "STORAGE_UPLOAD_FAILED"})
    public DeliveryStatusResponseDto pickupFinishByDreami(
            @PathVariable UUID orderId, @LoginUser UUID dreamiId,
            @Valid @RequestBody DeliveryPhotoRequest request) {
        return deliveryService.pickupFinishByDreami(orderId, dreamiId, request.photoKey());
    }

    @Operation(summary = "드리미의 픽업 취소", description = "픽업 과정에서 드리미가 취소한다. 이 배달에 배정된 드리미 본인만 취소할 수 있다.")
    @PostMapping("/orders/{orderId}/cancel/dreami")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"NOT_ASSIGNED_DREAMI", "CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByDreami(@PathVariable UUID orderId, @LoginUser UUID dreamiId) {
        return deliveryService.cancelByDreami(orderId, dreamiId);
    }

    @Operation(summary = "부르미의 픽업 취소", description = "픽업 과정에서 부르미가 취소한다. 이 주문을 접수한 부르미 본인만 취소할 수 있다.")
    @PostMapping("/orders/{orderId}/cancel/boormi")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"NOT_ORDER_BOORMI", "CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByBoormi(@PathVariable UUID orderId, @LoginUser UUID boormiId) {
        return deliveryService.cancelByBoormi(orderId, boormiId);
    }

    // TODO: 관리자 권한(role) 검증 필요. 현재 코드베이스에 admin role 개념이 없어 요청자 신원을 검증하지 못한다.
    //       role 시스템 도입 후 @LoginUser + 관리자 역할 확인을 추가해야 한다(그 전까지는 노출 주의).
    @Operation(summary = "관리자의 픽업 취소", description = "픽업 과정에서 관리자가 취소한다.")
    @PostMapping("/orders/{orderId}/cancel/admin")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED"})
    public DeliveryStatusResponseDto cancelByAdmin(@PathVariable UUID orderId) {
        return deliveryService.cancelByAdmin(orderId);
    }

    @Operation(summary = "드리미 배달 완료",
            description = "드리미가 배달(픽업 아님)을 완료하면 배달 완료 상태로 전이한다. 업로드한 배달 완료 인증 사진의 key를 함께 보낸다.")
    @PostMapping("/orders/{orderId}/finish")
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"NOT_ASSIGNED_DREAMI", "DELIVERY_NOT_FOUND",
                    "DELIVERY_ALREADY_CANCELLED", "DELIVERY_ALREADY_COMPLETED",
                    "DELIVERY_COMPLETION_NOT_ALLOWED_BEFORE_PICKUP"})
    @ApiErrorCodes(enumClass = UploadErrorCode.class,
            codes = {"FILE_NOT_FOUND", "KEY_OWNER_MISMATCH", "STORAGE_UPLOAD_FAILED"})
    public DeliveryStatusResponseDto finishDelivery(
            @PathVariable UUID orderId, @LoginUser UUID dreamiId,
            @Valid @RequestBody DeliveryPhotoRequest request) {
        return deliveryService.finishDelivery(orderId, dreamiId, request.photoKey());
    }
}
