package com.naengsam.quick.domain.dreami.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오퍼(수락 전 콜)의 물품 사진 조회 응답. 부르미가 등록한 사진이 없거나 조회 실패 시 itemPhotoUrl은 null이다.
 */
public record OfferItemPhotoDto(
        // @Schema: Swagger(OpenAPI) 문서에 이 필드의 설명·예시·nullable 여부를 표시하기 위한 annotation.
        // 로직에는 영향을 주지 않고 API 문서 생성에만 쓰인다.
        @Schema(description = "물품 사진 다운로드 URL. 사진이 없거나 조회 실패 시 null",
                example = "https://s3.ap-northeast-2.amazonaws.com/...", nullable = true)
        String itemPhotoUrl
) {
}
