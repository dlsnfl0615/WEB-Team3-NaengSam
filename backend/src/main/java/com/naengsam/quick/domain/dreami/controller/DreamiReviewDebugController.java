package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.dto.DreamiReviewDto;
import com.naengsam.quick.domain.dreami.dto.DreamiReviewRejectRequest;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.global.session.PublicApi;
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
 * 드리미 신분증/범죄경력 인증 신청을 사람이 직접 검수해 승인/반려하기 위한 임시 관리자 페이지용 API. 인증 없이 열려 있으며 운영 배포 전 제거 또는 비활성화가 필요하다.
 */
@Tag(name = "[Debug] Dreami Review", description = "드리미 인증 신청을 수동으로 검수하는 임시 관리자 전용 API")
@RestController
@RequestMapping("/api/v1/debug/dreami-review")
@RequiredArgsConstructor
@PublicApi
public class DreamiReviewDebugController {

    private final DreamiService dreamiService;

    @Operation(summary = "검수 대기 중인 드리미 인증 신청 목록 조회")
    @GetMapping("/pending")
    public List<DreamiReviewDto> pending() {
        return dreamiService.listPendingReviews();
    }

    @Operation(summary = "드리미 인증 신청 승인")
    @PostMapping("/{dreamiId}/approve")
    public void approve(@PathVariable UUID dreamiId) {
        dreamiService.approveReview(dreamiId);
    }

    @Operation(summary = "드리미 인증 신청 반려")
    @PostMapping("/{dreamiId}/reject")
    public void reject(@PathVariable UUID dreamiId, @RequestBody DreamiReviewRejectRequest request) {
        dreamiService.rejectReview(dreamiId, request.reason());
    }
}
