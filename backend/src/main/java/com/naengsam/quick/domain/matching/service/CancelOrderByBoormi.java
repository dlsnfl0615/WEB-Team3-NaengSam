package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record CancelOrderByBoormi(MatchingService service, UUID orderId) implements Action {

    @Override
    public void execute() {
        service.applyCancelOrderByBoormi(orderId);
    }
}
