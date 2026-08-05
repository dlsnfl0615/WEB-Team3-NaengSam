package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.dreami.dto.DreamiOnlineRequest;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void acceptOffer(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.acceptByDreami(offerId);
    }

    @Operation(summary = "드리미가 제안 거절", description = "로그인한 드리미에게 온 제안을 거절한다.")
    @PostMapping("/offers/{offerId}/reject")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void rejectOffer(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.rejectByDreami(offerId);
    }

    @Operation(summary = "주변 콜 리스트 조회",
            description = "내 위치·동선 기준으로 콜을 정렬해 리스트/지도뷰로 제공하며 예상 수익·소요시간을 함께 표시한다.")
    @PostMapping("/calls/nearby")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    public List<NearbyCallDto> findNearbyCalls(@Valid @RequestBody NearbyOrderRequest request) {
        return dreamiService.findNearbyCalls(request);
    }

    @Operation(summary = "현재 수행 중인 배달 카드 조회", description = "드리미가 현재 수행 중인 배달 건의 카드 정보를 조회한다.")
    @GetMapping("/deliveries/current/card")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    public OrderSummaryDto findCurrentDeliveryCard(@LoginUser UUID dreamiId) {
        return dreamiService.findCurrentDeliveryCard(dreamiId);
    }

    @Operation(summary = "현재 수행 중인 배달 취소",
            description = "드리미가 픽업 전인 현재 수행 중인 배달을 취소한다. 픽업 이후에는 취소할 수 없다.")
    @DeleteMapping("/deliveries/current")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND"})
    @ApiErrorCodes(enumClass = DeliveryErrorCode.class,
            codes = {"CANCELLATION_RESTRICTED_DURING_DELIVERY", "DELIVERY_ALREADY_CANCELLED", "DELIVERY_NOT_FOUND"})
    public void cancelCurrentDelivery(@LoginUser UUID dreamiId) {
        dreamiService.cancelCurrentDelivery(dreamiId);
    }
}
