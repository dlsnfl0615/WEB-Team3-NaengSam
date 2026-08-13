package com.naengsam.quick.domain.dreami.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오퍼(수락 전 콜)의 물품 사진 조회 응답. 부르미가 등록한 사진이 없거나 조회 실패 시 itemPhotoUrl은 null이다.
 */
public record OfferItemPhotoDto(
        @Schema(description = "물품 사진 다운로드 URL. 사진이 없거나 조회 실패 시 null",
                example = "https://s3.ap-northeast-2.amazonaws.com/...", nullable = true)
        String itemPhotoUrl
) {
}
