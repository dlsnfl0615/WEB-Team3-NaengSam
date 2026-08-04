package com.naengsam.quick.domain.matching.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OfferTimeoutScheduler가 예약된 TTL에 맞춰 올바른 Action을 MatchingEngine에 제출하는지 검증한다. TTL을 호출부에서 직접 주입하므로, 실제
 * OFFER_TTL(30초)을 기다릴 필요 없이 밀리초 단위 지연만으로 동일한 동작을 검증할 수 있다.
 */
class OfferTimeoutSchedulerTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private OfferTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        matchingService = mock(MatchingService.class);
        scheduler = new OfferTimeoutScheduler(matchingEngine, matchingService);
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
}
