package com.naengsam.quick.domain.matching.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 오퍼 timeout을 DelayQueue에 등록해두고, 만료 시각이 되면 전용 워커 스레드가 꺼내 해당 Action을 MatchingEngine에 제출한다. 워커 스레드는 직접 상태를 변경하지
 * 않는다 — 모든 상태 변경은 MatchingEngine의 단일 소비 스레드에서만 일어난다.
 */
@Slf4j
@Component
public class OfferTimeoutScheduler {

    private final DelayQueue<MatchingTimeout> timeoutQueue = new DelayQueue<>();
    private final MatchingEngine matchingEngine;
    private final MatchingService matchingService;

    public OfferTimeoutScheduler(MatchingEngine matchingEngine, @Lazy MatchingService matchingService) {
        this.matchingEngine = matchingEngine;
        this.matchingService = matchingService;
    }

    @PostConstruct
    public void start() {
        Thread.ofVirtual().name("offer-timeout-worker").start(this::run);
    }

    private void run() {
        while (true) {
            try {
                MatchingTimeout timeout = timeoutQueue.take();
                timeout.execute(matchingEngine, matchingService);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 한 timeout 처리 실패로 워커 스레드 전체가 죽지 않도록 방어한다.
                log.error("오퍼 timeout 처리 실패", e);
            }
        }
    }

    public void scheduleDreamiOfferTimeout(UUID offerId, Duration ttl) {
        timeoutQueue.put(DreamiOfferTimeout.after(offerId, ttl));
    }

    public void scheduleBoormiOfferTimeout(UUID offerId, Duration ttl) {
        timeoutQueue.put(BoormiOfferTimeout.after(offerId, ttl));
    }
}
