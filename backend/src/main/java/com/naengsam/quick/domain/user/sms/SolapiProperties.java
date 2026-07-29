package com.naengsam.quick.domain.user.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOLAPI 연동 설정. {@code solapi.*} (application.properties) 로 바인딩된다.
 */
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
        boolean enabled,
        String apiKey,
        String apiSecret,
        String from
) {
}
