package com.naengsam.quick.domain.delivery.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 드리미 픽업/배달 완료 요청. 드리미가 자기 세션으로 발급받아 업로드한 인증 사진의 S3 key를 담아 보낸다.
 */
public record DeliveryPhotoRequest(
        @NotBlank String photoKey
) {
}
