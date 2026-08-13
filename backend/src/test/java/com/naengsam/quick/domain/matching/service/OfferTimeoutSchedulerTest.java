package com.naengsam.quick.domain.matching.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OfferTimeoutScheduler가 드리미/부르미 오퍼 timeout Action을 만들어 MatchingEngine에 정확한 지연으로 예약만 하는지 검증한다. 자체
 * 큐/워커가 없으므로(=MatchingEngine.schedule 호출이 곧 예약 완료) 실행 시각까지 기다릴 필요가 없다.
 */
class OfferTimeoutSchedulerTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private OfferTimeoutScheduler offerTimeoutScheduler;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        matchingService = mock(MatchingService.class);
        offerTimeoutScheduler = new OfferTimeoutScheduler(matchingEngine, matchingService);
    }

    @Test
    void 드리미_오퍼_timeout을_예약하면_ExpireDreamiOffer가_ttl만큼의_지연으로_엔진에_예약된다() {
        UUID offerId = UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(30);

        offerTimeoutScheduler.scheduleDreamiOfferTimeout(offerId, ttl);

        verify(matchingEngine).schedule(eq(new ExpireDreamiOffer(matchingService, offerId)), eq(ttl));
    }

    @Test
    void 부르미_오퍼_timeout을_예약하면_ExpireBoormiOffer가_ttl만큼의_지연으로_엔진에_예약된다() {
        UUID offerId = UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(30);

        offerTimeoutScheduler.scheduleBoormiOfferTimeout(offerId, ttl);

        verify(matchingEngine).schedule(eq(new ExpireBoormiOffer(matchingService, offerId)), eq(ttl));
    }

    @Test
    void 여러_오퍼의_timeout이_각각_독립적으로_예약된다() {
        UUID firstOfferId = UUID.randomUUID();
        UUID secondOfferId = UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(30);

        offerTimeoutScheduler.scheduleDreamiOfferTimeout(firstOfferId, ttl);
        offerTimeoutScheduler.scheduleDreamiOfferTimeout(secondOfferId, ttl);

        verify(matchingEngine).schedule(eq(new ExpireDreamiOffer(matchingService, firstOfferId)), eq(ttl));
        verify(matchingEngine).schedule(eq(new ExpireDreamiOffer(matchingService, secondOfferId)), eq(ttl));
        verifyNoMoreInteractions(matchingEngine);
    }
}
