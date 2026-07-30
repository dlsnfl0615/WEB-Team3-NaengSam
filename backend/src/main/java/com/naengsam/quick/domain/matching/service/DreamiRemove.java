package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record DreamiRemove(MatchingService service, UUID dreamiId) implements Action {

    @Override
    public void execute() {
        service.applyRemoveDreami(dreamiId);
    }
}
