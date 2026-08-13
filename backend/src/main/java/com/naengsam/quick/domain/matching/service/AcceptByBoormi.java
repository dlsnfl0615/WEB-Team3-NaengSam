package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.Action;
import java.util.UUID;

record AcceptByBoormi(MatchingService service, UUID offerId) implements Action {

    @Override
    public void execute() {
        service.applyAcceptByBoormi(offerId);
    }
}
