package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.dto.DreamiReviewDto;
import com.naengsam.quick.domain.dreami.dto.DreamiReviewRejectRequest;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.session.AdminUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 드리미 신분증/범죄경력 인증 신청을 사람이 직접 검수해 승인/반려하기 위한 관리자 페이지용 API. 관리자 계정으로 로그인해야 호출할 수 있다({@link AdminUser}).
 */
@Tag(name = "[Admin] Dreami Review", description = "드리미 인증 신청을 수동으로 검수하는 관리자 전용 API")
@RestController
@RequestMapping("/api/v1/admin/dreami-review")
@RequiredArgsConstructor
public class DreamiApproveController {

    private final DreamiService dreamiService;

    @Operation(summary = "검수 대기 중인 드리미 인증 신청 목록 조회")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @GetMapping("/pending")
    public List<DreamiReviewDto> pending(@AdminUser UUID adminId) {
        return dreamiService.listPendingReviews();
    }

    @Operation(summary = "드리미 인증 신청 승인")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @PostMapping("/{dreamiId}/approve")
    public void approve(@PathVariable UUID dreamiId, @AdminUser UUID adminId) {
        dreamiService.approveReview(dreamiId);
    }

    @Operation(summary = "드리미 인증 신청 반려")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @PostMapping("/{dreamiId}/reject")
    public void reject(@PathVariable UUID dreamiId, @RequestBody DreamiReviewRejectRequest request,
            @AdminUser UUID adminId) {
        dreamiService.rejectReview(dreamiId, request.reason());
    }
}
