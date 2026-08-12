package com.naengsam.quick.domain.matching.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingActionScheduler가 예약된 지연/주기에 맞춰 올바른 Action을 MatchingEngine에 제출하는지 검증한다. 지연·주기를 호출부에서 직접
 * 밀리초 단위로 주입하므로, 실제 오퍼 TTL이나 batch window를 기다릴 필요 없이 동일한 동작을 빠르게 검증할 수 있다. 오퍼 timeout(한 번 실행)과 배치 매칭
 * 사이클(반복 실행)이 같은 스케줄러를 공유하므로, 두 시나리오를 모두 이 테스트에서 다룬다.
 */
class MatchingActionSchedulerTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private MatchingActionScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        matchingService = mock(MatchingService.class);
        scheduler = new MatchingActionScheduler(matchingEngine, matchingService);
        scheduler.start();
    }

    @Test
    void 드리미_오퍼_timeout이_만료되면_ExpireDreamiOffer가_제출된다() {
        UUID offerId = UUID.randomUUID();

        scheduler.scheduleDreamiOfferTimeout(offerId, Duration.ZERO);

        verify(matchingEngine, timeout(500)).submit(new ExpireDreamiOffer(matchingService, offerId));
    }

    @Test
    void 부르미_오퍼_timeout이_만료되면_ExpireBoormiOffer가_제출된다() {
        UUID offerId = UUID.randomUUID();

        scheduler.scheduleBoormiOfferTimeout(offerId, Duration.ZERO);

        verify(matchingEngine, timeout(500)).submit(new ExpireBoormiOffer(matchingService, offerId));
    }

    @Test
    void 아직_만료_시각이_되지_않은_timeout은_즉시_제출되지_않는다() {
        UUID offerId = UUID.randomUUID();

        scheduler.scheduleDreamiOfferTimeout(offerId, Duration.ofMillis(300));

        verify(matchingEngine, after(100).never()).submit(any());
        verify(matchingEngine, timeout(1000)).submit(new ExpireDreamiOffer(matchingService, offerId));
    }

    @Test
    void 여러_드리미의_timeout이_섞여_있어도_각각_독립적으로_처리된다() {
        UUID firstOfferId = UUID.randomUUID();
        UUID secondOfferId = UUID.randomUUID();

        scheduler.scheduleDreamiOfferTimeout(secondOfferId, Duration.ofMillis(200));
        scheduler.scheduleDreamiOfferTimeout(firstOfferId, Duration.ZERO);

        verify(matchingEngine, timeout(500)).submit(new ExpireDreamiOffer(matchingService, firstOfferId));
        verify(matchingEngine, timeout(500)).submit(new ExpireDreamiOffer(matchingService, secondOfferId));
    }

    @Test
    void 하나의_timeout_제출이_예외를_던져도_워커_스레드는_죽지_않고_다음_timeout을_계속_처리한다() {
        UUID failingOfferId = UUID.randomUUID();
        UUID succeedingOfferId = UUID.randomUUID();
        when(matchingEngine.submit(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(true);

        scheduler.scheduleDreamiOfferTimeout(failingOfferId, Duration.ZERO);
        scheduler.scheduleDreamiOfferTimeout(succeedingOfferId, Duration.ZERO);

        verify(matchingEngine, timeout(500)).submit(new ExpireDreamiOffer(matchingService, succeedingOfferId));
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
