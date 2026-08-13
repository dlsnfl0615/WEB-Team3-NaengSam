package com.naengsam.quick.domain.matching.service;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingActionScheduler가 예약된 지연/주기에 맞춰 임의의 Action을 MatchingEngine에 제출하는지 검증한다. 지연·주기를 호출부에서 직접
 * 밀리초 단위로 주입하므로, 실제 batch window를 기다릴 필요 없이 동일한 동작을 빠르게 검증할 수 있다. 배치 매칭 사이클(반복 실행)이 이 스케줄러를
 * 사용한다(오퍼 timeout은 {@link OfferTimeoutScheduler}로 분리되어 별도 테스트에서 다룬다).
 */
class MatchingActionSchedulerTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private MatchingActionScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        matchingService = mock(MatchingService.class);
        scheduler = new MatchingActionScheduler(matchingEngine);
        scheduler.start();
    }

    @Test
    void 지정된_지연_후_임의의_Action을_한_번_제출한다() {
        Action action = new RunMatchingAssignmentCycle(matchingService);

        scheduler.schedule(action, Duration.ZERO);

        verify(matchingEngine, timeout(500).times(1)).submit(action);
    }

    @Test
    void 반복_예약은_지정한_주기마다_같은_Action을_반복해서_제출한다() {
        Action action = new RunMatchingAssignmentCycle(matchingService);

        scheduler.scheduleRepeating(action, Duration.ofMillis(50));

        verify(matchingEngine, timeout(1000).atLeast(2)).submit(action);
    }

    @Test
    void 한_번만_예약된_Action은_반복_실행되지_않고_한_번만_실행된다() {
        Action action = new RunMatchingAssignmentCycle(matchingService);

        scheduler.schedule(action, Duration.ZERO);

        verify(matchingEngine, timeout(500)).submit(action);
        verify(matchingEngine, after(200).times(1)).submit(action);
    }

    @Test
    void 반복_예약된_Action의_제출이_실패해도_다음_주기에_다시_제출되어_예약_상태가_복구된다() {
        Action action = new RunMatchingAssignmentCycle(matchingService);
        when(matchingEngine.submit(action))
                .thenThrow(new RuntimeException("engine submit 실패"))
                .thenReturn(true);

        scheduler.scheduleRepeating(action, Duration.ofMillis(50));

        verify(matchingEngine, timeout(1000).atLeast(2)).submit(action);
    }
}
