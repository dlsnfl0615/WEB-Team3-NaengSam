package com.naengsam.quick.domain.delivery.dto;

import java.util.UUID;

public record DreamiRemove(MatchingContext context, UUID dreamiId) implements Action {

    @Override
    public void execute() {
        context.applyRemoveDreami(dreamiId);
    }
}
