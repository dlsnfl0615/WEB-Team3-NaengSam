package com.naengsam.quick.domain.matching.service.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 즉시/지연/반복 Action을 하나의 DelayQueue와 단일 워커 스레드로 처리한다. 실행 시각이 같으면 제출 순서(sequence)로 정렬해, delay가 0인
 * 즉시 제출도 FIFO가 보장된다. 모든 매칭 상태 변경을 이 스레드 하나로 직렬화하는 것이 목적이다.
 */
@Slf4j
@Component
public class MatchingEngine {

    private final DelayQueue<QueuedAction> queue = new DelayQueue<>();
    private final AtomicLong sequencer = new AtomicLong();
    private volatile Thread worker;

    /**
     * Action을 즉시(지연 없이) 큐에 제출한다. 같은 시각에 제출된 다른 Action들과는 제출 순서(FIFO)가 보장된다.
     */
    public boolean submit(Action action) {
        log.debug("액션 큐에 등록: {}", action);
        return queue.offer(QueuedAction.of(action, Duration.ZERO, sequencer.getAndIncrement()));
    }

    /**
     * 지정한 지연 시간 뒤에 Action을 한 번 큐에 제출한다.
     */
    public void schedule(Action action, Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay는 음수일 수 없습니다: " + delay);
        }
        queue.put(QueuedAction.of(action, delay, sequencer.getAndIncrement()));
    }

    /**
     * 지정한 간격마다 반복해서 Action을 큐에 제출한다(fixed-delay: 각 실행이 끝난 시각 기준으로 다음 회차를 예약).
     */
    public void scheduleRepeating(Action action, Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("반복 주기는 0보다 커야 합니다: " + interval);
        }
        queue.put(QueuedAction.repeating(action, interval, sequencer.getAndIncrement()));
    }

    @PostConstruct
    public void start() {
        worker = Thread.ofVirtual().name("matching-engine").start(this::run);
    }

    @PreDestroy
    public void shutdown() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
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
                log.error("매칭 액션 실행 실패", e);
            }
        }
    }

    private void dispatch(QueuedAction queuedAction) {
        try {
            queuedAction.action().execute();
        } finally {
            // 실행 성공/실패와 무관하게, 반복 예약이면 다음 회차를 다시 큐에 넣어 예약 상태를 복구한다.
            if (queuedAction.isRepeating()) {
                queue.put(queuedAction.next());
            }
        }
    }
}
