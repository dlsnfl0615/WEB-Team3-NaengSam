package com.naengsam.quick.domain.matching.service.scheduler;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;

/**
 * {@link MatchingScheduler}의 DelayQueue에 등록되는 항목. 실행 시각(executeAtNanos)이 같으면 제출 순서(sequence)로
 * 우선순위를 정해 즉시 제출(delay=0)도 FIFO가 보장되게 한다. repeatInterval이 있으면 실행 후 같은 간격으로 다음 회차가 다시 예약된다.
 * 실행 시각은 벽시계(NTP 보정으로 뒤로 튈 수 있는 currentTimeMillis)가 아니라 단조 증가하는 {@link System#nanoTime()} 기준으로
 * 계산한다.
 */
final class ScheduledAction implements Delayed {

    private final Action action;
    private final long executeAtNanos;
    private final long sequence;
    private final Duration repeatInterval;
    private final Cancellation cancellation;

    private ScheduledAction(Action action, long executeAtNanos, long sequence, Duration repeatInterval,
            Cancellation cancellation) {
        this.action = action;
        this.executeAtNanos = executeAtNanos;
        this.sequence = sequence;
        this.repeatInterval = repeatInterval;
        this.cancellation = cancellation;
    }

    static ScheduledAction after(Action action, Duration delay, long sequence) {
        return new ScheduledAction(action, System.nanoTime() + delay.toNanos(), sequence, null,
                new Cancellation());
    }

    static ScheduledAction repeating(Action action, Duration interval, long sequence) {
        return new ScheduledAction(action, System.nanoTime() + interval.toNanos(), sequence, interval,
                new Cancellation());
    }

    Action action() {
        return action;
    }

    boolean isRepeating() {
        return repeatInterval != null;
    }

    boolean isCancelled() {
        return cancellation.isCancelled();
    }

    ScheduledActionHandle handle() {
        return new ScheduledActionHandle(cancellation);
    }

    /**
     * 취소 상태(cancellation)를 그대로 물려받은 다음 회차를 만든다. 취소는 회차와 무관하게 예약 전체에 적용되어야 하기 때문이다.
     */
    ScheduledAction next(long sequence) {
        return new ScheduledAction(action, System.nanoTime() + repeatInterval.toNanos(), sequence,
                repeatInterval, cancellation);
    }

    /**
     * 반복 예약의 모든 회차가 공유하는 취소 상태. cancel()은 호출자 스레드에서, isCancelled()는 스케줄러 워커 스레드에서 호출되는
     * 서로 다른 스레드 간의 신호이므로 가시성 보장이 필요해 AtomicBoolean을 쓴다.
     */
    static final class Cancellation {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        void cancel() {
            cancelled.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(executeAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(@NonNull Delayed other) {
        ScheduledAction that = (ScheduledAction) other;
        int byDeadline = Long.compare(this.executeAtNanos, that.executeAtNanos);
        if (byDeadline != 0) {
            return byDeadline;
        }
        return Long.compare(this.sequence, that.sequence);
    }
}
