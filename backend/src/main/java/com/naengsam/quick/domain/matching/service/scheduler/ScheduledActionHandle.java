package com.naengsam.quick.domain.matching.service.scheduler;

/**
 * {@link MatchingScheduler#schedule(Action, java.time.Duration)}, {@link MatchingScheduler#scheduleRepeating}가
 * 반환하는 예약 취소 핸들. 반복 예약은 같은 {@link ScheduledAction.Cancellation}을 회차마다 공유하므로, 한 번 취소하면 이후
 * 회차도 실행되지 않는다.
 */
public final class ScheduledActionHandle {

    private final ScheduledAction.Cancellation cancellation;

    ScheduledActionHandle(ScheduledAction.Cancellation cancellation) {
        this.cancellation = cancellation;
    }

    public void cancel() {
        cancellation.cancel();
    }

    public boolean isCancelled() {
        return cancellation.isCancelled();
    }
}
