package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.Action;
import java.util.UUID;

record RejectByDreami(MatchingService service, UUID offerId) implements Action {

    @Override
    public void execute() {
        service.applyRejectByDreami(offerId);
    }
}
