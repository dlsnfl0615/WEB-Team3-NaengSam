package com.naengsam.quick.domain.matching.controller;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.MatchingStartRequest;
import com.naengsam.quick.domain.matching.dto.OrderOfferGroupDto;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.PublicApi;
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
 * 매칭 상태를 수동으로 조작/조회하기 위한 디버그 전용 API. 인증 없이 열려 있으며 운영 배포 전 제거 또는 비활성화가 필요하다.
 */
@Tag(name = "[Debug] Matching", description = "매칭 흐름을 수동으로 조작/조회하는 디버그 전용 API")
@RestController
@RequestMapping("/api/v1/debug/matching")
@RequiredArgsConstructor
@PublicApi
public class MatchingDebugController {

    private final MatchingService matchingService;

    @Operation(summary = "드리미 등록")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/dreamis")
    public UUID registerDreami(@RequestBody GeoPoint location) {
        UUID dreamiId = UUID.randomUUID();
        if (!matchingService.registerDreami(dreamiId, location)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
        return dreamiId;
    }

    @Operation(summary = "드리미 제거")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @DeleteMapping("/dreamis/{dreamiId}")
    public void removeDreami(@PathVariable UUID dreamiId) {
        if (!matchingService.removeDreami(dreamiId)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    @Operation(summary = "대기중인 드리미 목록 조회")
    @GetMapping("/dreamis")
    public List<DreamiView> waitingDreamis() {
        return matchingService.waitingDreamis().stream()
                .map(DreamiView::from)
                .toList();
    }

    @Operation(summary = "주문에 대한 매칭 시작 요청 (제안 방 생성은 비동기로 처리됨)",
            description = "요청이 수락되면 매칭 엔진 큐에 등록된다. 실제 방 생성 여부는 /orders/{orderId}/group 으로 폴링해 확인한다.")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/orders/{orderId}/start")
    public void startMatching(@PathVariable UUID orderId, @Valid @RequestBody MatchingStartRequest request) {
        Orders order = Orders.create(orderId, request.boormiId(),
                request.destination().latitude(), request.destination().longitude());
        if (!matchingService.startMatching(order)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    @Operation(summary = "주문의 매칭 방(OrderOfferGroup) 상태 조회")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"NOT_FOUND"})
    @GetMapping("/orders/{orderId}/group")
    public OrderOfferGroupDto getOrderOfferGroup(@PathVariable UUID orderId) {
        return matchingService.findOrderOfferGroup(orderId)
                .map(OrderOfferGroupDto::from)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.NOT_FOUND));
    }

    @Operation(summary = "드리미가 제안 수락")
    @PostMapping("/offers/{offerId}/dreami-accept")
    public void acceptByDreami(@PathVariable UUID offerId) {
        matchingService.acceptByDreami(offerId);
    }

    @Operation(summary = "드리미가 제안 거절")
    @PostMapping("/offers/{offerId}/dreami-reject")
    public void rejectByDreami(@PathVariable UUID offerId) {
        matchingService.rejectByDreami(offerId);
    }

    @Operation(summary = "부르미가 제안 수락")
    @PostMapping("/offers/{offerId}/boormi-accept")
    public void acceptByBoormi(@PathVariable UUID offerId) {
        matchingService.acceptByBoormi(offerId);
    }

    @Operation(summary = "부르미가 제안 거절")
    @PostMapping("/offers/{offerId}/boormi-reject")
    public void rejectByBoormi(@PathVariable UUID offerId) {
        matchingService.rejectByBoormi(offerId);
    }

    @Operation(summary = "드리미 응답 시간 만료 처리")
    @PostMapping("/offers/{offerId}/dreami-expire")
    public void expireDreamiOffer(@PathVariable UUID offerId) {
        matchingService.expireDreamiOffer(offerId);
    }

    @Operation(summary = "부르미 응답 시간 만료 처리")
    @PostMapping("/offers/{offerId}/boormi-expire")
    public void expireBoormiOffer(@PathVariable UUID offerId) {
        matchingService.expireBoormiOffer(offerId);
    }

    @Operation(summary = "부르미가 매칭 진행 중인 주문을 취소")
    @ApiErrorCodes(enumClass = GeneralErrorCode.class, codes = {"CONFLICT"})
    @PostMapping("/orders/{orderId}/cancel")
    public void cancelOrderByBoormi(@PathVariable UUID orderId) {
        if (!matchingService.cancelOrderByBoormi(orderId)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    @Operation(summary = "서버 폴백용 모든 요청 재매칭")
    @PostMapping("/orders/rematch")
    public void rematchWaitingGroups() {
        matchingService.scheduleRematchWaitingGroups();
    }

    record DreamiView(UUID dreamiId, GeoPoint location,
                      MatchingService.WaitingDreamiStatus status, LocalDateTime updatedAt) {

        static DreamiView from(MatchingService.WaitingDreami dreami) {
            return new DreamiView(dreami.dreamiId(), dreami.location(), dreami.status(), dreami.updatedAt());
        }
    }
}
