package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record ExpireDreamiOffer(MatchingService service, UUID offerId) implements Action {

    @Override
    public void execute() {
        service.applyExpireDreamiOffer(offerId);
    }
}
