package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 지연 실행이 필요한 Action을 DelayQueue에 등록해두고, 실행 시각이 되면 전용 워커 스레드가 꺼내 MatchingEngine에 제출한다. 워커 스레드는 Action을
 * 제출하기만 할 뿐 직접 상태를 변경하지 않는다 — 모든 상태 변경은 MatchingEngine의 단일 소비 스레드에서만 일어난다. 오퍼 timeout처럼 한 번만 실행되는
 * Action과 배치 매칭 사이클처럼 일정 주기로 반복 실행되는 Action이 이 스케줄러 하나를 함께 사용한다.
 */
@Slf4j
@Component
public class MatchingActionScheduler {

    private final DelayQueue<ScheduledAction> queue = new DelayQueue<>();
    private final MatchingEngine matchingEngine;
    private final MatchingService matchingService;

    public MatchingActionScheduler(MatchingEngine matchingEngine, @Lazy MatchingService matchingService) {
        this.matchingEngine = matchingEngine;
        this.matchingService = matchingService;
    }

    @PostConstruct
    public void start() {
        Thread.ofVirtual().name("matching-action-scheduler").start(this::run);
    }

    private void run() {
        while (true) {
            try {
                ScheduledAction scheduled = queue.take();
                dispatch(scheduled);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 한 Action 처리 실패로 워커 스레드 전체가 죽지 않도록 방어한다.
                log.error("지연 Action 처리 실패", e);
            }
        }
    }

    private void dispatch(ScheduledAction scheduled) {
        try {
            matchingEngine.submit(scheduled.action());
        } finally {
            // 제출 성공 여부와 무관하게, 반복 예약이면 다음 회차를 다시 큐에 넣어 예약 상태를 복구한다.
            if (scheduled.isRepeating()) {
                queue.put(scheduled.next());
            }
        }
    }

    /**
     * 지정한 지연 시간 뒤에 Action을 한 번 MatchingEngine에 제출한다.
     */
    public void schedule(Action action, Duration delay) {
        queue.put(ScheduledAction.after(action, delay));
    }

    /**
     * 지정한 간격마다 반복해서 Action을 MatchingEngine에 제출한다. 매 회차 제출 성공 여부와 무관하게 다음 회차가 다시 예약된다.
     */
    public void scheduleRepeating(Action action, Duration interval) {
        queue.put(ScheduledAction.repeating(action, interval));
    }

    public void scheduleDreamiOfferTimeout(UUID offerId, Duration ttl) {
        schedule(new ExpireDreamiOffer(matchingService, offerId), ttl);
    }

    public void scheduleBoormiOfferTimeout(UUID offerId, Duration ttl) {
        schedule(new ExpireBoormiOffer(matchingService, offerId), ttl);
    }
}
