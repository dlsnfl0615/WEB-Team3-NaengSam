package com.naengsam.quick.global.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹푸시(VAPID) 연동 설정. {@code web-push.*} (application.properties) 로 바인딩된다.
 *
 * <p>키 쌍은 저장소 밖에서 한 번 생성해 환경변수로 주입한다({@code npx web-push generate-vapid-keys}, P-256).
 * {@code enabled=false}면 {@link WebPushSender} 빈이 아예 등록되지 않아 웹푸시 채널이 조용히 건너뛰어진다.
 * 이 설정 자체는 비활성일 때도 바인딩되므로, 공개키 조회 엔드포인트가 "미설정"을 응답할 수 있다.
 */
@ConfigurationProperties(prefix = "web-push")
public record WebPushProperties(
        boolean enabled,
        String publicKey,
        String privateKey,
        String subject
) {

    /**
     * 브라우저에 내려줄 수 있는 공개키가 준비됐는지 여부. 비활성이거나 키가 비어 있으면 프론트는 구독을 시도하지 않는다.
     */
    public boolean hasPublicKey() {
        return publicKey != null && !publicKey.isBlank();
    }
}
