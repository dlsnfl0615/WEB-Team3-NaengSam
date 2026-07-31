package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record RejectByBoormi(MatchingService service, UUID offerId) implements Action {

    @Override
    public void execute() {
        service.applyRejectByBoormi(offerId);
    }
}
