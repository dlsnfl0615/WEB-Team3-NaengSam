package com.naengsam.quick.global.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 구독 해제 요청. 엔드포인트가 곧 기기 식별자이므로 이것만 있으면 대상을 특정할 수 있다.
 */
public record PushUnsubscribeRequest(
        @NotBlank
        @Size(max = 512)
        String endpoint
) {
}
