package com.naengsam.quick.domain.order.controller;

import com.naengsam.quick.domain.order.dto.ReviewContentRequest;
import com.naengsam.quick.domain.order.dto.ReviewDto;
import com.naengsam.quick.domain.order.dto.ReviewScoreRequest;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.service.ReviewService;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 완료된 배달 건에 대한 상호 리뷰 API. 부르미와 드리미가 같은 엔드포인트를 쓰고, 서버가 로그인 사용자로 리뷰 대상(상대방)을 판별한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "리뷰컨트롤러", description = "완료된 주문에 대해 상대방에게 별점과 리뷰 내용을 남긴다")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "별점 등록", description = "완료된 주문의 상대방에게 별점을 남긴다. 리뷰 내용은 이후 PATCH 로 채운다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "CANNOT_REVIEW", "ALREADY_REVIEWED"})
    @PostMapping("/{orderId}/review")
    public ReviewDto writeScore(@PathVariable UUID orderId, @LoginUser UUID userId,
            @Valid @RequestBody ReviewScoreRequest request) {
        return reviewService.writeScore(orderId, userId, request.score());
    }

    @Operation(summary = "리뷰 내용 등록", description = "먼저 남긴 별점 리뷰에 내용을 채우거나 수정한다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "REVIEW_NOT_FOUND"})
    @PatchMapping("/{orderId}/review")
    public ReviewDto writeContent(@PathVariable UUID orderId, @LoginUser UUID userId,
            @Valid @RequestBody ReviewContentRequest request) {
        return reviewService.writeContent(orderId, userId, request.content());
    }

    @Operation(summary = "내가 남긴 리뷰 조회", description = "별점만 남긴 상태로 다시 들어왔을 때 기존 리뷰를 확인한다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class,
            codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER", "REVIEW_NOT_FOUND"})
    @GetMapping("/{orderId}/review")
    public ReviewDto getMyReview(@PathVariable UUID orderId, @LoginUser UUID userId) {
        return reviewService.getMyReview(orderId, userId);
    }

    @Operation(summary = "받은 리뷰 조회", description = "이 주문에서 상대방이 나에게 남긴 별점·리뷰 내용을 조회한다. 아직 안 남겼으면 result가 null이다.")
    @ApiErrorCodes(enumClass = OrderErrorCode.class, codes = {"ORDER_NOT_FOUND", "NOT_ORDER_OWNER"})
    @GetMapping("/{orderId}/review/received")
    public ReviewDto getReceivedReview(@PathVariable UUID orderId, @LoginUser UUID userId) {
        return reviewService.getReceivedReview(orderId, userId);
    }
}
