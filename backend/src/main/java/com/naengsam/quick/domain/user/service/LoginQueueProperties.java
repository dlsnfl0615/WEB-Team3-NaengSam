package com.naengsam.quick.domain.user.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 대기열 튜닝값. {@code login.queue.*} (application.properties) 로 바인딩된다.
 *
 * <ul>
 *   <li>permits : 동시에 해싱할 수 있는 개수. PBKDF2 는 CPU 바운드라 vCPU 수를 넘기면 총 처리량은
 *       그대로인 채 개별 지연만 늘고, 톰캣 스레드가 전부 해싱에 묶여 나머지 API 가 굶는다</li>
 *   <li>capacity : 대기열 최대 인원. 초과분은 대기시키지 않고 즉시 거부한다</li>
 *   <li>ticketTtl/readyTtl : 대기 티켓 유효기간 · 처리 완료 후 클레임 가능 시간</li>
 *   <li>estimatedHashDuration : 예상 대기시간·폴링 주기 산식의 입력값. {@code login.hash} 타이머의
 *       실측 p50 으로 맞춘다</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "login.queue")
public record LoginQueueProperties(
        int permits,
        int capacity,
        Duration ticketTtl,
        Duration readyTtl,
        Duration estimatedHashDuration
) {
}
