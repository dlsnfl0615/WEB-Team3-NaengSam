package com.naengsam.quick.global.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 연결 튜닝값. {@code sse.*} (application.properties) 로 바인딩된다.
 */
@ConfigurationProperties(prefix = "sse")
public record SseProperties(
        Duration heartbeatInterval,
        Duration connectionTimeout
) {
}
