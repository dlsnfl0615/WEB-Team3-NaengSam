package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.scheduler.Action;
import java.util.UUID;

record CancelOrderByBoormi(MatchingService service, UUID orderId) implements Action {

    @Override
    public void execute() {
        service.applyCancelOrderByBoormi(orderId);
    }
}
