package com.naengsam.quick.domain.user.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 휴대폰 인증 관련 튜닝값. {@code verification.*} (application.properties) 로 바인딩된다.
 *
 * <ul>
 *   <li>codeTtl/resendCooldown/verifiedTtl : 인증번호 유효기간 · 재발송 쿨다운 · 인증완료 유지시간</li>
 *   <li>maxVerifyAttempts : 인증번호 검증 실패 허용 횟수(초과 시 코드 폐기, brute-force 차단)</li>
 *   <li>phone/global 계열 : 발송 남용(문자폭탄/과금) 방지용 고정 윈도우 rate limit</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "verification")
public record VerificationProperties(
        Duration codeTtl,
        Duration resendCooldown,
        Duration verifiedTtl,
        int maxVerifyAttempts,
        Duration phoneWindow,
        int phoneMax,
        Duration globalWindow,
        int globalMax
) {
}
