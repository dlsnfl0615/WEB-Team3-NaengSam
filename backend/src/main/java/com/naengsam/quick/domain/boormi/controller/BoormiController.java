package com.naengsam.quick.domain.boormi.controller;

import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.service.BoormiService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "주문 접수", description = "출발지·도착지·물건 정보로 주문을 생성·저장하고 결제와 매칭을 시작한다.")
    @PostMapping("/calls")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class,
            codes = {"EXTERNAL_SERVICE_ERROR", "EXTERNAL_SERVICE_TIMEOUT", "CONFLICT"})
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"SAME_ORIGIN_DESTINATION", "TOO_MANY_ACTIVE_ORDERS"})
    public UUID subscribeOrder(@LoginUser UUID boormiId, @Valid @RequestBody OrderRequest orderRequest) {
        return boormiService.subscribeOrder(orderRequest, boormiId);
    }

    @Operation(summary = "주문 취소", description = "매칭 성사 전 상태의 주문을 취소하고 매칭 큐에서 제안을 회수한다.")
    @DeleteMapping("/calls/{orderId}")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "CANNOT_CANCEL_AFTER_PICKUP"})
    public void unsubscribeOrder(@LoginUser UUID boormiId, @PathVariable UUID orderId) {
        boormiService.unsubscribeOrder(boormiId, orderId);
    }
}
