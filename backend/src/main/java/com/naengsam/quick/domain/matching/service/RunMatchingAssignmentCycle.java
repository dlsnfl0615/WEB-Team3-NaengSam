package com.naengsam.quick.domain.matching.service;

record RunMatchingAssignmentCycle(MatchingService service) implements Action {

    @Override
    public void execute() {
        service.applyRunMatchingAssignmentCycle();
    }
}
