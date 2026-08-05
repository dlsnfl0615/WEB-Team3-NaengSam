package com.naengsam.quick.domain.boormi.controller;

import com.naengsam.quick.domain.boormi.dto.ConfirmDreamiRequest;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.dto.RejectDreamiRequest;
import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/boormi")
@Tag(name = "부르미 컨트롤러", description = "부르미의 주문 견적/주문 관련 API")
public class BoormiController {

    private final BoormiService boormiService;

    @Operation(summary = "예상 견적 조회", description = "출발지·도착지·물건유형으로 예상 가격/시간/거리를 계산한다.")
    @PostMapping("/expected-value")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"EXTERNAL_SERVICE_ERROR", "EXTERNAL_SERVICE_TIMEOUT"})
    public ExpectedValueDto expectedValue(@Valid @RequestBody ExpectedValueRequest request) {
        return boormiService.expectedValue(request);
    }

    @Operation(summary = "주문 접수", description = "출발지·도착지·물건 정보로 주문을 생성·저장하고 결제와 매칭을 시작한다. 배달 요금·예상시간은 클라이언트 값을 신뢰하지 않고 서버가 견적과 동일한 로직으로 재계산해 저장한다.")
    @PostMapping("/calls")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class,
            codes = {"EXTERNAL_SERVICE_ERROR", "EXTERNAL_SERVICE_TIMEOUT", "CONFLICT"})
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"SAME_ORIGIN_DESTINATION", "TOO_MANY_ACTIVE_ORDERS"})
    public UUID subscribeOrder(@LoginUser UUID boormiId, @Valid @RequestBody OrderRequest orderRequest) {
        return boormiService.subscribeOrder(orderRequest, boormiId);
    }

    @Operation(summary = "내 주문 목록 조회", description = "로그인한 부르미가 신청한 주문을 최신순 커서 페이지네이션으로 조회한다. status 로 단일 상태 필터링이 가능하며, cursor 는 이전 응답의 nextCursor 를 그대로 넘긴다.")
    @GetMapping("/calls")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"INVALID_CURSOR"})
    public BoormiOrdersResponse getMyOrders(
            @LoginUser UUID boormiId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderCd status) {
        return boormiService.getMyOrders(boormiId, cursor, size, status);
    }

    @Operation(summary = "주문 취소", description = "매칭 성사 전 상태의 주문을 취소하고 매칭 큐에서 제안을 회수한다.")
    @DeleteMapping("/calls/{orderId}")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "CANNOT_CANCEL_AFTER_PICKUP"})
    public void unsubscribeOrder(@LoginUser UUID boormiId, @PathVariable UUID orderId) {
        boormiService.unsubscribeOrder(boormiId, orderId);
    }

    @Operation(summary = "드리미 최종 확정(더블 컨펌)",
            description = "부르미가 수락한 드리미를 최종 확정한다. 주문을 IN_PROGRESS 로 전이하고 매칭엔진에 부르미 수락을 제출한다. offerId 는 드리미 수락 시 받은 dreami_info 의 값이다.")
    @PostMapping("/calls/{orderId}/confirm-dreami")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "INVALID_DREAMI_CONFIRMATION", "NO_DREAMI_TO_CONFIRM"})
    public void confirmDreami(@LoginUser UUID boormiId, @PathVariable UUID orderId,
            @Valid @RequestBody ConfirmDreamiRequest request) {
        boormiService.confirmDreami(boormiId, orderId, request.offerId());
    }

    @Operation(summary = "드리미 거절(더블 컨펌)",
            description = "부르미가 수락한 드리미를 거절한다. 주문을 다시 MATCHING 으로 되돌리고 매칭엔진에 부르미 거절을 제출해 재매칭을 시도한다. offerId 는 드리미 수락 시 받은 dreami_info 의 값이다.")
    @PostMapping("/calls/{orderId}/reject-dreami")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "INVALID_DREAMI_REJECTION"})
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void rejectDreami(@LoginUser UUID boormiId, @PathVariable UUID orderId,
            @Valid @RequestBody RejectDreamiRequest request) {
        boormiService.rejectDreami(boormiId, orderId, request.offerId());
    }
}
