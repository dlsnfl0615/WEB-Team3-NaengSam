package com.naengsam.quick.domain.user.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인증문자 발송 남용(문자폭탄/과금 공격) 방지용 인메모리 rate limit.
 *
 * <p>고정 윈도우 카운터 3종을 둔다.
 * <ul>
 *   <li>IP별   : 한 호스트가 임의의 번호들을 순회하며 대량 발송하는 것을 차단</li>
 *   <li>번호별 : 60초 쿨다운을 보완해 특정 피해자를 지속 타깃팅하는 것을 차단</li>
 *   <li>전역   : IP·번호를 모두 회전시켜도 총 발송량을 묶는 하드 백스톱(SOLAPI 과금 상한)</li>
 * </ul>
 *
 * <p>단일 인스턴스 인메모리 전제({@link VerificationCodeStore}와 동일 철학)이며, 세 카운터를 가로지르는
 * 원자성까지는 보장하지 않는 best-effort 방식이다(동시 요청이 상한을 근소하게 넘길 수 있으나 목적상 허용).
 * 세 검사를 모두 통과했을 때만 실제 발송으로 간주해 카운터를 증가시킨다(거부된 요청은 카운터를 소모하지 않음).
 */
@Component
public class SmsSendRateLimiter {

    private record Window(LocalDateTime start, int count) {
    }

    private final VerificationProperties props;
    private final ConcurrentHashMap<String, Window> phoneCounters = new ConcurrentHashMap<>();
    private final AtomicReference<Window> globalCounter = new AtomicReference<>();

    public SmsSendRateLimiter(VerificationProperties props) {
        this.props = props;
    }

    /**
     * 번호·전역 한도를 모두 만족하면 발송을 허용하고 세 카운터를 증가시킨다. 하나라도 초과면 {@code false}(증가 없음).
     */
    public boolean tryAcquire(String phone) {
        LocalDateTime now = LocalDateTime.now();
        if (isOverLimit(phoneCounters.get(phone), now, props.phoneWindow(), props.phoneMax())
                || isOverLimit(globalCounter.get(), now, props.globalWindow(), props.globalMax())) {
            return false;
        }
        increment(phoneCounters, phone, now, props.phoneWindow());
        globalCounter.updateAndGet(w -> next(w, now, props.globalWindow()));
        return true;
    }

    private static boolean isOverLimit(Window window, LocalDateTime now, Duration windowSize, int max) {
        return window != null
                && !now.isAfter(window.start().plus(windowSize))
                && window.count() >= max;
    }

    private static void increment(ConcurrentHashMap<String, Window> counters, String key,
                                  LocalDateTime now, Duration windowSize) {
        counters.compute(key, (k, w) -> next(w, now, windowSize));
    }

    private static Window next(Window current, LocalDateTime now, Duration windowSize) {
        if (current == null || now.isAfter(current.start().plus(windowSize))) {
            return new Window(now, 1);
        }
        return new Window(current.start(), current.count() + 1);
    }

    /**
     * 만료된 윈도우 엔트리를 주기적으로 제거해 임의 번호 회전으로 인한 메모리 증가를 막는다.
     */
    @Scheduled(fixedDelay = 3_600_000L)
    void sweepExpired() {
        LocalDateTime now = LocalDateTime.now();
        phoneCounters.entrySet().removeIf(e -> now.isAfter(e.getValue().start().plus(props.phoneWindow())));
    }
}
