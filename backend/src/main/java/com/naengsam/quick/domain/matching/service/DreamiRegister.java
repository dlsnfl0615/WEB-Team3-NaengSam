package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.util.UUID;

record DreamiRegister(MatchingService service, UUID dreamiId, GeoPoint location) implements Action {

    @Override
    public void execute() {
        service.applyRegisterDreami(dreamiId, location);
    }
}
