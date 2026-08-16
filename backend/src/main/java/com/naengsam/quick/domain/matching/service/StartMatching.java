package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.order.entity.Orders;

record StartMatching(MatchingService service, Orders order) implements Action {

    @Override
    public void execute() {
        service.applyStartMatching(order);
    }
}
