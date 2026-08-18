package com.naengsam.quick.domain.matching.controller;

import com.naengsam.quick.domain.matching.dto.CurrentMatchingStatusDto;
import com.naengsam.quick.domain.matching.dto.CurrentMatchingStatusDto.PendingOfferDto;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiDto;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiRequest;
import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.matching.service.NearbyDreamiFinder;
import com.naengsam.quick.global.session.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 사용자를 위한 매칭 상태 조회 운영용 API. 임의로 상태를 조작할 수 있는 {@link MatchingAdminController}와는 분리되어 있으며, 로그인 세션 기준으로만
 * 자신의 매칭 상태를 조회한다.
 */
@Tag(name = "매칭컨트롤러", description = "로그인 사용자의 매칭 상태를 조회하는 API")
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final NearbyDreamiFinder nearbyDreamiFinder;

    @Operation(summary = "현재 매칭 상태 조회",
            description = "드리미로서 응답 대기 중인 제안(pendingOffer)과, 부르미로서 확인 대기 중인 드리미 수락 정보(incomingDreami)를 반환한다. "
                    + "진행 중인 것이 없으면 해당 필드는 null이다. "
                    + "dreamiOnline은 드리미가 매칭엔진에 등록돼 오퍼를 기다리는 중인지다(새로고침 후 온라인 상태 복원용).")
    @GetMapping("/current")
    public CurrentMatchingStatusDto getCurrentStatus(@LoginUser UUID userId) {
        PendingOfferDto pendingOffer = matchingService.findPendingOfferForDreami(userId)
                .flatMap(offer -> matchingService.findOrderOfferGroup(offer.orderId())
                        .map(group -> PendingOfferDto.from(offer, group, matchingService.offerTtl())))
                .orElse(null);
        DreamiInfoPayload incomingDreami = matchingService.findIncomingDreamiOffer(userId)
                .map(offer -> DreamiInfoPayload.from(offer, matchingService.pickupEtaMinutesForOffer(offer),
                        matchingService.boormiOfferTtl()))
                .orElse(null);
        return new CurrentMatchingStatusDto(pendingOffer, incomingDreami,
                matchingService.isDreamiWaiting(userId));
    }

    @Operation(summary = "주변 대기 드리미 조회",
            description = "부르미 매칭 지도용. 기준 좌표 반경 내에서 콜을 기다리는 드리미를 최대 10명까지 가까운 순으로 반환한다.")
    @PostMapping("/dreamis/nearby")
    public List<NearbyDreamiDto> findNearbyWaitingDreamis(
            @Valid @RequestBody NearbyDreamiRequest request) {
        return nearbyDreamiFinder.find(request);
    }
}
