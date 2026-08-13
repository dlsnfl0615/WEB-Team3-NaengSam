package com.naengsam.quick.domain.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 픽업 인증 사진 조회 응답. 아직 픽업 전이거나 조회 실패 시 pickupPhotoUrl은 null이다.
 */
public record PickupPhotoDto(
        @Schema(description = "픽업 인증 사진 다운로드 URL. 픽업 전이거나 조회 실패 시 null",
                example = "https://s3.ap-northeast-2.amazonaws.com/...", nullable = true)
        String pickupPhotoUrl
) {
}
