package com.naengsam.quick.domain.matching.controller;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.MatchingStartRequest;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiDto;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiRequest;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.dto.OrderOfferGroupDto;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.model.WaitingOrder;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.matching.service.NearbyDreamiFinder;
import com.naengsam.quick.domain.matching.service.NearbyOrderFinder;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.AdminUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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

/**
 * 매칭 상태를 수동으로 조작/조회하기 위한 디버그 전용 API. 관리자 계정으로 로그인해야 호출할 수 있다({@link AdminUser}).
 */
@Tag(name = "[Admin] Matching", description = "매칭 흐름을 수동으로 조작/조회하는 관리자 전용 API")
@RestController
@RequestMapping("/api/v1/admin/matching")
@RequiredArgsConstructor
public class MatchingAdminController {

    private final MatchingService matchingService;
    private final NearbyDreamiFinder nearbyDreamiFinder;
    private final NearbyOrderFinder nearbyOrderFinder;

    @Operation(summary = "반경 내 드리미 위치 조회",
            description = "기준 좌표에서 반경(m) 이내에 있는 드리미를 최대 10명까지 가까운 순으로 반환한다.")
    @PostMapping("/dreamis/nearby")
    public List<NearbyDreamiDto> findNearbyDreamis(@Valid @RequestBody NearbyDreamiRequest request,
            @AdminUser UUID adminId) {
        return nearbyDreamiFinder.find(request);
    }

    @Operation(summary = "반경 내 주문 위치 조회",
            description = "기준 좌표에서 반경(m) 이내에 있는 대기중인 주문을 최대 10개까지 가까운 순으로 반환한다. 한 부르미가 여러 주문을 가질 수 있으므로 주문 단위로 조회한다.")
    @PostMapping("/orders/nearby")
    public List<NearbyOrderDto> findNearbyOrders(@Valid @RequestBody NearbyOrderRequest request,
            @AdminUser UUID adminId) {
        return nearbyOrderFinder.find(request);
    }

    @Operation(summary = "대기중인 주문 목록 조회")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @GetMapping("/orders/waiting")
    public List<OrderView> waitingOrders(@AdminUser UUID adminId) {
        return matchingService.waitingOrders().stream()
                .map(OrderView::from)
                .toList();
    }

    @Operation(summary = "드리미 등록")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/dreamis")
    public UUID registerDreami(@Valid @RequestBody GeoPoint location, @AdminUser UUID adminId) {
        UUID dreamiId = UUID.randomUUID();
        if (!matchingService.registerDreami(dreamiId, location)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
        return dreamiId;
    }

    @Operation(summary = "드리미 제거")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @DeleteMapping("/dreamis/{dreamiId}")
    public void removeDreami(@PathVariable UUID dreamiId, @AdminUser UUID adminId) {
        if (!matchingService.removeDreami(dreamiId)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    @Operation(summary = "대기중인 드리미 목록 조회")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @GetMapping("/dreamis")
    public List<DreamiView> waitingDreamis(@AdminUser UUID adminId) {
        return matchingService.waitingDreamis().stream()
                .map(DreamiView::from)
                .toList();
    }

    @Operation(summary = "주문에 대한 매칭 시작 요청 (제안 방 생성은 비동기로 처리됨)",
            description = "요청이 수락되면 매칭 엔진 큐에 등록된다. 실제 방 생성 여부는 /orders/{orderId}/group 으로 폴링해 확인한다.")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/orders/{orderId}/start")
    public void startMatching(@PathVariable UUID orderId, @Valid @RequestBody MatchingStartRequest request,
            @AdminUser UUID adminId) {
        // 디버그: 출발지 좌표가 없어 도착지 좌표를 origin/destination 에 동일하게 사용한다.
        Orders order = Orders.create(orderId, request.boormiId(),
                request.destination(), request.destination());
        if (!matchingService.startMatching(order)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    @Operation(summary = "주문의 매칭 방(OrderOfferGroup) 상태 조회")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"NOT_FOUND"})
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @GetMapping("/orders/{orderId}/group")
    public OrderOfferGroupDto getOrderOfferGroup(@PathVariable UUID orderId, @AdminUser UUID adminId) {
        return matchingService.findOrderOfferGroup(orderId)
                .map(OrderOfferGroupDto::from)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.NOT_FOUND));
    }

    @Operation(summary = "드리미가 제안 수락")
    @PostMapping("/offers/{offerId}/dreami-accept")
    public void acceptByDreami(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.acceptByDreami(offerId);
    }

    @Operation(summary = "드리미가 제안 거절")
    @PostMapping("/offers/{offerId}/dreami-reject")
    public void rejectByDreami(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.rejectByDreami(offerId);
    }

    @Operation(summary = "부르미가 제안 수락")
    @PostMapping("/offers/{offerId}/boormi-accept")
    public void acceptByBoormi(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.acceptByBoormi(offerId);
    }

    @Operation(summary = "부르미가 제안 거절")
    @PostMapping("/offers/{offerId}/boormi-reject")
    public void rejectByBoormi(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.rejectByBoormi(offerId);
    }

    @Operation(summary = "드리미 응답 시간 만료 처리")
    @PostMapping("/offers/{offerId}/dreami-expire")
    public void expireDreamiOffer(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.expireDreamiOffer(offerId);
    }

    @Operation(summary = "부르미 응답 시간 만료 처리")
    @PostMapping("/offers/{offerId}/boormi-expire")
    public void expireBoormiOffer(@PathVariable UUID offerId, @AdminUser UUID adminId) {
        matchingService.expireBoormiOffer(offerId);
    }

    @Operation(summary = "매칭 진행 중인 주문을 취소")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/orders/{orderId}/cancel")
    public void cancelOrderByBoormi(@PathVariable UUID orderId, @AdminUser UUID adminId) {
        if (!matchingService.cancelOrderByBoormi(orderId)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    record DreamiView(UUID dreamiId, GeoPoint location,
                      WaitingDreamiStatus status, LocalDateTime updatedAt) {

        static DreamiView from(WaitingDreami dreami) {
            return new DreamiView(dreami.dreamiId(), dreami.location(), dreami.status(), dreami.updatedAt());
        }
    }

    record OrderView(UUID orderId, GeoPoint location) {

        static OrderView from(WaitingOrder order) {
            return new OrderView(order.orderId(), order.location());
        }
    }
}
