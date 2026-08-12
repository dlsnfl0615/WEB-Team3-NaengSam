package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.Action;
import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * {@link MatchingActionScheduler}의 DelayQueue에 등록되는 항목. repeatInterval이 있으면 실행 후 같은 간격으로 다시 예약되는 반복
 * Action(예: 배치 매칭 사이클)이고, 없으면 한 번만 실행되는 Action(예: 오퍼 timeout)이다.
 */
record ScheduledAction(Action action, long executeAtMillis, Duration repeatInterval) implements Delayed {

    static ScheduledAction after(Action action, Duration delay) {
        return new ScheduledAction(action, System.currentTimeMillis() + delay.toMillis(), null);
    }

    static ScheduledAction repeating(Action action, Duration interval) {
        return new ScheduledAction(action, System.currentTimeMillis() + interval.toMillis(), interval);
    }

    boolean isRepeating() {
        return repeatInterval != null;
    }

    ScheduledAction next() {
        return repeating(action, repeatInterval);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(executeAtMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(@NonNull Delayed other) {
        return Long.compare(executeAtMillis, ((ScheduledAction) other).executeAtMillis());
    }
}
