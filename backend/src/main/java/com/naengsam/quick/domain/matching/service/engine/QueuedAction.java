package com.naengsam.quick.domain.matching.service.engine;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * {@link MatchingEngine}의 DelayQueue에 등록되는 항목. repeatInterval이 있으면 실행 후 같은 간격으로 다시 예약되는 반복
 * Action(예: 배치 매칭 사이클)이고, 없으면 한 번만 실행되는 Action(예: 즉시 제출·오퍼 timeout)이다. deadline은
 * {@link System#nanoTime()} 기준이라 시스템 시각이 바뀌어도(NTP 보정 등) 영향받지 않는다. sequence는 같은 deadline을 가진
 * 항목들(대표적으로 delay 0인 즉시 제출)의 제출 순서를 보존해 FIFO를 보장한다.
 */
record QueuedAction(Action action, long deadlineNanos, long sequence, Duration repeatInterval) implements Delayed {

    static QueuedAction of(Action action, Duration delay, long sequence) {
        return new QueuedAction(action, System.nanoTime() + delay.toNanos(), sequence, null);
    }

    static QueuedAction repeating(Action action, Duration interval, long sequence) {
        return new QueuedAction(action, System.nanoTime() + interval.toNanos(), sequence, interval);
    }

    boolean isRepeating() {
        return repeatInterval != null;
    }

    /**
     * fixed-delay 방식으로 다음 회차를 만든다. deadline은 "이번 실행이 끝난 시각 + interval"이지, 이전 deadline + interval이
     * 아니다 — 실행 자체가 오래 걸려도 다음 회차가 그만큼 밀릴 뿐 몰아서 실행되지 않는다.
     */
    QueuedAction next() {
        return repeating(action, repeatInterval, sequence);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(@NonNull Delayed other) {
        if (other instanceof QueuedAction queuedAction) {
            int result = Long.compare(deadlineNanos, queuedAction.deadlineNanos);
            return result != 0 ? result : Long.compare(sequence, queuedAction.sequence);
        }
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }
}
