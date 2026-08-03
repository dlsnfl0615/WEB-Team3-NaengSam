package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record DreamiOfferTimeout(UUID offerId, long executeAtMillis) implements MatchingTimeout {

    @Override
    public void execute(MatchingEngine matchingEngine, MatchingService matchingService) {
        matchingEngine.submit(new ExpireDreamiOffer(matchingService, offerId));
    }
}
