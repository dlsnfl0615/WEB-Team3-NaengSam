package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.GeoPoint;
import java.util.UUID;

record DreamiRegister(MatchingService service, UUID dreamiId, GeoPoint location) implements Action {

    @Override
    public void execute() {
        service.applyRegisterDreami(dreamiId, location);
    }
}
