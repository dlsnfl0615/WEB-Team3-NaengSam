package com.naengsam.quick.domain.matching.service;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * DelayQueue에 등록되는 오퍼 timeout. 만료 시각이 되면 워커 스레드가 꺼내 해당 Action을 MatchingEngine에 제출한다.
 */
public sealed interface MatchingTimeout extends Delayed permits DreamiOfferTimeout, BoormiOfferTimeout {

    long executeAtMillis();

    @Override
    default long getDelay(TimeUnit unit) {
        return unit.convert(executeAtMillis() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    default int compareTo(@NonNull Delayed other) {
        return Long.compare(executeAtMillis(), ((MatchingTimeout) other).executeAtMillis());
    }
}
