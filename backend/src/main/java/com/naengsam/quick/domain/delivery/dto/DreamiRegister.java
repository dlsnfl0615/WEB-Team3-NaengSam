package com.naengsam.quick.domain.delivery.dto;

import java.util.UUID;

public record DreamiRegister(MatchingContext context, UUID dreamiId, GeoPoint location) implements Action {

    @Override
    public void execute() {
        context.applyRegisterDreami(dreamiId, location);
    }
}
