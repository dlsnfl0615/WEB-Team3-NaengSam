package com.naengsam.quick.global.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 브라우저 {@code PushSubscription.toJSON()} 결과를 그대로 받는 구독 등록 요청.
 *
 * <p>필드 모양을 브라우저 표준 출력과 일치시켜 두었기 때문에 프론트는 {@code JSON.stringify(subscription)}을
 * 별도 매핑 없이 그대로 보낼 수 있다(중첩 {@code keys} 객체가 그 역할이다). 표준 출력에 함께 들어 있는
 * {@code expirationTime}은 쓰지 않으므로 받지 않고 무시한다.
 */
public record PushSubscriptionRequest(
        @NotBlank
        @Size(max = 512, message = "지원하지 않는 길이의 push 엔드포인트입니다.")
        String endpoint,

        @NotNull
        @Valid
        Keys keys
) {

    /**
     * 페이로드 암호화에 쓰는 클라이언트 키 쌍(base64url).
     */
    public record Keys(
            @NotBlank @Size(max = 255) String p256dh,
            @NotBlank @Size(max = 255) String auth
    ) {
    }
}
