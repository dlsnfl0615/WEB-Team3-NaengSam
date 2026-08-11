package com.naengsam.quick.domain.matching.service.scheduler;

/**
 * {@link MatchingScheduler}가 실행하는 작업 단위. 스케줄러의 단일 워커 스레드에서만 실행된다.
 */
@FunctionalInterface
public interface Action {

    void execute();
}
