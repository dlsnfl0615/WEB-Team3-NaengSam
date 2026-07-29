package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 발송 남용 방지 rate limit 단위 테스트.번호/전역 고정 윈도우 한도, 거부 요청의 카운터 비소모, 윈도우 경과 후 리셋을 검증한다.
 */
class SmsSendRateLimiterTest {

    private static VerificationProperties props(Duration phoneWindow, int phoneMax,
                                                Duration globalWindow, int globalMax) {
        return new VerificationProperties(
                Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofMinutes(30), 5,
                phoneWindow, phoneMax, globalWindow, globalMax);
    }

    @Test
    void 번호별_한도를_초과하면_거부한다() {
        SmsSendRateLimiter limiter = new SmsSendRateLimiter(
                props(Duration.ofHours(24), 2, Duration.ofHours(24), 100));

        assertThat(limiter.tryAcquire("01011112222")).isTrue();
        assertThat(limiter.tryAcquire("01011112222")).isTrue();
        assertThat(limiter.tryAcquire("01011112222")).isFalse();
    }

    @Test
    void 전역_한도를_초과하면_거부한다() {
        SmsSendRateLimiter limiter = new SmsSendRateLimiter(
                props(Duration.ofHours(24), 100, Duration.ofHours(24), 2));

        assertThat(limiter.tryAcquire("01000000001")).isTrue();
        assertThat(limiter.tryAcquire("01000000002")).isTrue();
        assertThat(limiter.tryAcquire("01000000003")).isFalse();
    }
}
