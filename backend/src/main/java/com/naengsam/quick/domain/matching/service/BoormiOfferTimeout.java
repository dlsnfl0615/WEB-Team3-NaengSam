package com.naengsam.quick.domain.matching.service;

import java.time.Duration;
import java.util.UUID;

record BoormiOfferTimeout(UUID offerId, long executeAtMillis) implements MatchingTimeout {

    static BoormiOfferTimeout after(UUID offerId, Duration ttl) {
        return new BoormiOfferTimeout(offerId, System.currentTimeMillis() + ttl.toMillis());
    }

    @Override
    public void execute(MatchingEngine matchingEngine, MatchingService matchingService) {
        matchingEngine.submit(new ExpireBoormiOffer(matchingService, offerId));
    }
}
