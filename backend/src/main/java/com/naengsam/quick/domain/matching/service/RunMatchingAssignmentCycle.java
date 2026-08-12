package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.scheduler.Action;

record RunMatchingAssignmentCycle(MatchingService service) implements Action {

    @Override
    public void execute() {
        service.applyRunMatchingAssignmentCycle();
    }
}
