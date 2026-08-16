package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션이 준비되면 배치 매칭 사이클을 {@code matching.batch-interval} 주기로 반복 실행되도록 한 번만 예약한다.
 * 최초 실행은 interval이 지난 뒤이며, 이후 배치는 취소되지 않고 계속 반복된다.
 */
@Component
public class PeriodicMatchingBatchScheduler {

    private final MatchingEngine matchingEngine;
    private final MatchingPolicyProperties matchingPolicyProperties;
    private final MatchingService matchingService;

    public PeriodicMatchingBatchScheduler(MatchingEngine matchingEngine,
            MatchingPolicyProperties matchingPolicyProperties, MatchingService matchingService) {
        this.matchingEngine = matchingEngine;
        this.matchingPolicyProperties = matchingPolicyProperties;
        this.matchingService = matchingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        matchingEngine.scheduleRepeating(new RunMatchingAssignmentCycle(matchingService),
                matchingPolicyProperties.batchInterval());
    }
}
