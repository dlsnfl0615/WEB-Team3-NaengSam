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
@Tag(name = "[Admin] Dreami Review", description = "드리미 인증 신청을 수동으로 검수하는 관리자 전용 API") // Swagger(OpenAPI) 문서에서 이 컨트롤러를 묶어 보여줄 그룹 이름
@RestController // 이 클래스가 REST API 컨트롤러임을 표시(메서드 반환값이 JSON 응답 바디가 됨)
@RequestMapping("/api/v1/admin/dreami-review")
@RequiredArgsConstructor // Lombok: final 필드(dreamiService)를 받는 생성자를 자동 생성해줘서 별도 생성자 코드 없이 의존성 주입이 된다
public class DreamiApproveController {

    private final DreamiService dreamiService;

    @Operation(summary = "검수 대기 중인 드리미 인증 신청 목록 조회") // Swagger 문서용 API 설명
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"}) // 이 API가 던질 수 있는 에러코드를 문서에 표시(이 프로젝트가 만든 커스텀 annotation)
    @GetMapping("/pending")
    public List<DreamiReviewDto> pending(@AdminUser UUID adminId) { // @AdminUser: 로그인 세션에서 관리자 id를 꺼내 파라미터로 자동 주입해주는 이 프로젝트만의 커스텀 annotation
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
