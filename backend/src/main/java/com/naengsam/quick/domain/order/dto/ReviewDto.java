package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.order.entity.BoormiReview;
import com.naengsam.quick.domain.order.entity.DreamiReview;
import com.naengsam.quick.domain.order.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 리뷰 응답. reviewerRole 은 리뷰를 작성한 쪽(BOORMI 면 부르미가 드리미를 평가한 리뷰)이다. BOORMI_REVIEW 의 detail 컬럼도 content 로 통일해 내려준다.
 */
public record ReviewDto(
        UUID reviewId,
        UUID orderId,
        @Schema(description = "리뷰 작성자 역할", example = "BOORMI") Role reviewerRole,
        int score,
        @Schema(description = "리뷰 내용. 별점만 남긴 상태면 null", nullable = true) String content
) {

    public static ReviewDto from(DreamiReview review) {
        return new ReviewDto(review.getReviewId(), review.getOrderId(), Role.BOORMI, review.getScore(),
                review.getContent());
    }

    public static ReviewDto from(BoormiReview review) {
        return new ReviewDto(review.getReviewId(), review.getOrderId(), Role.DREAMI, review.getScore(),
                review.getDetail());
    }
}
