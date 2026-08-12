package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.service.scheduler.Action;

record RematchWaitingGroups(MatchingService service) implements Action {

    @Override
    public void execute() {
        service.applyRematchWaitingGroups();
    }
}
