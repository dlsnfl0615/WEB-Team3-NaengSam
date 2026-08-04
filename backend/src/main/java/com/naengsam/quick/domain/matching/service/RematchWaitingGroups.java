package com.naengsam.quick.domain.matching.service;

record RematchWaitingGroups(MatchingService service) implements Action {

    @Override
    public void execute() {
        service.applyRematchWaitingGroups();
    }
}
