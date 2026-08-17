package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.dto.DreamiDashboardDto;
import com.naengsam.quick.domain.dreami.dto.DreamiOnlineRequest;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.DreamiTodayStatsDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.dto.OfferItemPhotoDto;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderCountDto;
import com.naengsam.quick.domain.order.dto.OrderStatusCountDto;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dreami")
@Tag(name = "드리미 컨트롤러", description = "드리미 프로필을 조회하고 온라인/오프라인 상태를 전환한다.")
public class DreamiController {

    private final DreamiService dreamiService;
    private final MatchingService matchingService;

    @Operation(summary = "드리미 프로필 조회", description = "부르미가 드리미의 이름, 평점, 거절 횟수를 조회한다.")
    @GetMapping("/{dreamiId}")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = DreamiErrorCode.class, codes = {"NOT_FOUND"})
    public DreamiProfileDto getProfile(@PathVariable UUID dreamiId) {
        return dreamiService.getDreamiProfile(dreamiId);
    }

    @Operation(summary = "드리미 온라인 전환",
            description = "드리미가 콜 수신 가능한 온라인 상태로 전환하고 현재 위치를 등록한다. 온라인 상태의 드리미에게만 주변 콜이 노출된다.")
    @PostMapping("/status/online")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = DreamiErrorCode.class, codes = {"NOT_FOUND", "NOT_APPROVED", "ALREADY_HAS_ACTIVE_ORDER"})
    public void goOnline(@LoginUser UUID dreamiId, @Valid @RequestBody DreamiOnlineRequest request) {
        dreamiService.goOnline(dreamiId, new GeoPoint(request.latitude(), request.longitude()));
    }

    @Operation(summary = "드리미 오프라인 전환", description = "드리미가 콜 수신 불가능한 오프라인 상태로 전환한다.")
    @PostMapping("/status/offline")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public void goOffline(@LoginUser UUID dreamiId) {
        matchingService.removeDreami(dreamiId);
    }

    @Operation(summary = "드리미가 제안 수락", description = "로그인한 드리미에게 온 제안을 수락한다.")
    @PostMapping("/offers/{offerId}/accept")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER", "OFFER_EXPIRED"})
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    public void acceptOffer(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        dreamiService.acceptOffer(offerId, dreamiId);
    }

    @Operation(summary = "드리미가 제안 거절", description = "로그인한 드리미에게 온 제안을 거절한다.")
    @PostMapping("/offers/{offerId}/reject")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void rejectOffer(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        dreamiService.rejectOffer(offerId, dreamiId);
    }

    @Operation(summary = "오퍼 물품 사진 조회",
            description = "수락 전 콜(오퍼)에서 부르미가 등록한 물품 사진 URL을 조회한다. 이 오퍼를 받은 드리미 본인만 조회할 수 있다.")
    @GetMapping("/offers/{offerId}/item-photo")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    public OfferItemPhotoDto getOfferItemPhoto(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        return dreamiService.getOfferItemPhoto(offerId, dreamiId);
    }

    @Operation(summary = "주변 콜 리스트 조회",
            description = "내 위치·동선 기준으로 콜을 정렬해 리스트/지도뷰로 제공하며 예상 수익·소요시간을 함께 표시한다.")
    @PostMapping("/calls/nearby")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public List<NearbyCallDto> findNearbyCalls(@Valid @RequestBody NearbyOrderRequest request) {
        return dreamiService.findNearbyCalls(request);
    }

    @Operation(summary = "현재 수행 중인 배달 카드 조회",
            description = "드리미가 현재 수행 중인 배달 건의 카드 정보를 조회한다. 진행 중인 배달이 없으면 result가 null이다.")
    @GetMapping("/deliveries/current/card")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public OrderSummaryDto findCurrentDeliveryCard(@LoginUser UUID dreamiId) {
        return dreamiService.findCurrentDeliveryCard(dreamiId);
    }

    @Operation(summary = "드리미 대시보드 조회",
            description = "완료 건수, 누적 수익, 이번 주 정산 예정 금액을 조회한다.")
    @GetMapping("/dashboard")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public DreamiDashboardDto getDashboard(@LoginUser UUID dreamiId) {
        return dreamiService.getDashboard(dreamiId);
    }

    @Operation(summary = "드리미 오늘 통계 조회",
            description = "홈 화면에 보여줄 오늘 하루 스코프의 수익·완료 건수를 조회한다.")
    @GetMapping("/dashboard/today")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public DreamiTodayStatsDto getTodayStats(@LoginUser UUID dreamiId) {
        return dreamiService.getTodayStats(dreamiId);
    }

    @Operation(summary = "드리미 활동 내역 조회",
            description = "로그인한 드리미가 수행한(수행 중인) 배달을 최신순으로 페이지네이션 조회한다. status를 생략하면 상태 무관 전체, "
                    + "지정하면 그 상태들만 반환한다(필터 탭 하나가 여러 상태를 묶는 경우 여러 값을 함께 넘기면 된다. 예: "
                    + "status=MATCHING&status=PENDING_BOORMI_CONFIRMATION). cursor는 이전 응답의 nextCursor를 그대로 "
                    + "넘기면 되고, 첫 페이지는 생략한다.")
    @GetMapping("/deliveries")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"INVALID_CURSOR"})
    public BoormiOrdersResponse getDreamiOrders(@LoginUser UUID dreamiId,
            @RequestParam(required = false) List<OrderCd> status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return dreamiService.getMyOrders(dreamiId, status, cursor, size);
    }

    @Operation(summary = "내 배달 단건 조회",
            description = "배달 하나를 주문 id로 직접 조회한다. 활동 내역 상세 화면이 목록 페이지네이션을 거치지 않고 딥링크/새로고침으로 바로 들어왔을 때 쓴다.")
    @GetMapping("/deliveries/{orderId}")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER"})
    public OrderSummaryDto getDreamiOrder(@LoginUser UUID dreamiId, @PathVariable UUID orderId) {
        return dreamiService.getMyDelivery(dreamiId, orderId);
    }

    @Operation(summary = "내 배달 전체 건수 조회", description = "활동 내역 화면의 총 건수 표시용으로, 상태 무관하게 로그인한 드리미의 전체 배달 건수를 조회한다.")
    @GetMapping("/deliveries/count")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public OrderCountDto getDreamiOrderCount(@LoginUser UUID dreamiId) {
        return dreamiService.getMyDeliveryCount(dreamiId);
    }

    @Operation(summary = "내 배달 상태별 건수 조회",
            description = "활동 내역 화면의 탭(전체/진행중/완료/취소)별 개수 표시용. 목록 페이지네이션과 별개로 화면 진입 시 한 번만 호출하면 된다.")
    @GetMapping("/deliveries/status-counts")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    public List<OrderStatusCountDto> getDreamiOrderStatusCounts(@LoginUser UUID dreamiId) {
        return dreamiService.getMyDeliveryStatusCounts(dreamiId);
    }
}
