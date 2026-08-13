package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 오퍼 응답 timeout(드리미/부르미)에 필요한 Action을 만들어 {@link MatchingEngine}에 예약만 한다. 실행은 항상
 * {@link MatchingEngine}의 단일 워커 스레드에서 일어나므로, 이 컴포넌트는 별도 큐나 워커를 갖지 않는다 — "언제 timeout Action을
 * 제출할지"만 담당하는 얇은 스케줄러다.
 */
@Component
public class OfferTimeoutScheduler {

    private final MatchingEngine matchingEngine;
    private final MatchingService matchingService;

    public OfferTimeoutScheduler(MatchingEngine matchingEngine, @Lazy MatchingService matchingService) {
        this.matchingEngine = matchingEngine;
        this.matchingService = matchingService;
    }

    /**
     * 지정한 시간(ttl) 뒤에 드리미 응답 timeout(ExpireDreamiOffer)을 한 번 예약한다.
     */
    public void scheduleDreamiOfferTimeout(UUID offerId, Duration ttl) {
        matchingEngine.schedule(new ExpireDreamiOffer(matchingService, offerId), ttl);
    }

    /**
     * 지정한 시간(ttl) 뒤에 부르미 응답 timeout(ExpireBoormiOffer)을 한 번 예약한다.
     */
    public void scheduleBoormiOfferTimeout(UUID offerId, Duration ttl) {
        matchingEngine.schedule(new ExpireBoormiOffer(matchingService, offerId), ttl);
    }
}
