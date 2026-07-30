package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record AcceptByDreami(MatchingService service, UUID offerId) implements Action {

    @Override
    public void execute() {
        service.applyAcceptByDreami(offerId);
    }
}
