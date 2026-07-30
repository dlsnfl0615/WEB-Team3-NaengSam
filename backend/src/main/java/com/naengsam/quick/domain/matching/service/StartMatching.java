package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.Order;

record StartMatching(MatchingService service, Order order) implements Action {

    @Override
    public void execute() {
        service.applyStartMatching(order);
    }
}
