package com.naengsam.quick.domain.matching.controller;

import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/matching")
@Tag(name = "매칭컨트롤러", description = "부르미의 매칭 제안 수락·거절 컨트롤러입니다")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @Operation(summary = "드리미가 제안 수락", description = "로그인한 드리미에게 온 제안을 수락한다.")
    @PostMapping("/offers/{offerId}/dreami-accept")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void acceptByDreami(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.acceptByDreami(offerId);
    }

    @Operation(summary = "드리미가 제안 거절", description = "로그인한 드리미에게 온 제안을 거절한다.")
    @PostMapping("/offers/{offerId}/dreami-reject")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void rejectByDreami(@PathVariable UUID offerId, @LoginUser UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.rejectByDreami(offerId);
    }

    @Operation(summary = "부르미가 제안 수락", description = "로그인한 부르미 본인 주문에 대한 제안을 최종 승인한다.")
    @PostMapping("/offers/{offerId}/boormi-accept")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void acceptByBoormi(@PathVariable UUID offerId, @LoginUser UUID boormiId) {
        if (!matchingService.isBoormiOfferOwner(offerId, boormiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.acceptByBoormi(offerId);
    }

    @Operation(summary = "부르미가 제안 거절", description = "로그인한 부르미 본인 주문에 대한 제안을 거절한다.")
    @PostMapping("/offers/{offerId}/boormi-reject")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = MatchingErrorCode.class, codes = {"NOT_OFFER_OWNER"})
    public void rejectByBoormi(@PathVariable UUID offerId, @LoginUser UUID boormiId) {
        if (!matchingService.isBoormiOfferOwner(offerId, boormiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.rejectByBoormi(offerId);
    }
}
