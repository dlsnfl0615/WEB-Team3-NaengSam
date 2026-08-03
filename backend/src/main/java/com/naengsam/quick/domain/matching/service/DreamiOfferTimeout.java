package com.naengsam.quick.domain.matching.service;

import java.time.Duration;
import java.util.UUID;

record DreamiOfferTimeout(UUID offerId, long executeAtMillis) implements MatchingTimeout {

    static DreamiOfferTimeout after(UUID offerId, Duration ttl) {
        return new DreamiOfferTimeout(offerId, System.currentTimeMillis() + ttl.toMillis());
    }

    @Override
    public void execute(MatchingEngine matchingEngine, MatchingService matchingService) {
        matchingEngine.submit(new ExpireDreamiOffer(matchingService, offerId));
    }
}
