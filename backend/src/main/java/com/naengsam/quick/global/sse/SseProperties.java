package com.naengsam.quick.global.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 연결 튜닝값. {@code sse.*} (application.properties) 로 바인딩된다.
 *
 * <p>{@code maxConnectionsPerUser}는 사용자 한 명이 동시에 유지할 수 있는 연결(=열린 탭) 수 상한이다.
 * 상한을 넘는 구독은 새 emitter를 등록하지 않고 거부해 자원 고갈을 막는다.
 */
@ConfigurationProperties(prefix = "sse")
public record SseProperties(
        Duration heartbeatInterval,
        Duration connectionTimeout,
        int maxConnectionsPerUser
) {
}
