package com.naengsam.quick.domain.matching.service.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 즉시/지연/반복 Action을 하나의 DelayQueue와 단일 워커 스레드로 처리한다. 실행 시각이 같으면 제출 순서(sequence)로 정렬해, delay=0인
 * 즉시 제출도 FIFO가 보장된다. 매칭 도메인의 모든 Action(즉시·오퍼 timeout·배치 window·반복 재매칭 스캔)이 이 스케줄러 하나의 워커
 * 스레드에서 실행되어, 상태 변경의 단일 기록자 불변식을 유지한다.
 */
@Slf4j
@Component
public class MatchingScheduler {

    private final DelayQueue<ScheduledAction> queue = new DelayQueue<>();
    private final AtomicLong sequenceGenerator = new AtomicLong();
    private volatile Thread worker;

    @PostConstruct
    public void start() {
        worker = Thread.ofVirtual().name("matching-scheduler").start(this::run);
    }

    @PreDestroy
    public void shutdown() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }

    public boolean submit(Action action) {
        return queue.offer(ScheduledAction.after(action, Duration.ZERO, sequenceGenerator.getAndIncrement()));
    }

    public ScheduledActionHandle schedule(Action action, Duration delay) {
        ScheduledAction scheduled = ScheduledAction.after(action, delay, sequenceGenerator.getAndIncrement());
        queue.put(scheduled);
        return scheduled.handle();
    }

    public ScheduledActionHandle scheduleRepeating(Action action, Duration interval) {
        ScheduledAction scheduled = ScheduledAction.repeating(action, interval, sequenceGenerator.getAndIncrement());
        queue.put(scheduled);
        return scheduled.handle();
    }

    private void run() {
        while (true) {
            try {
                dispatch(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 큐 처리 자체에서 예외가 나도 워커 스레드가 죽지 않도록 방어한다.
                log.error("스케줄된 Action 처리 실패", e);
            }
        }
    }

    private void dispatch(ScheduledAction scheduled) {
        try {
            if (!scheduled.isCancelled()) {
                scheduled.action().execute();
            }
        } catch (Exception e) {
            // 한 Action의 실행 실패가 워커 스레드 전체를 죽이거나 다음 Action 처리를 막지 않도록 격리한다.
            log.error("Action 실행 실패", e);
        } finally {
            if (scheduled.isRepeating() && !scheduled.isCancelled()) {
                queue.put(scheduled.next(sequenceGenerator.getAndIncrement()));
            }
        }
    }
}
