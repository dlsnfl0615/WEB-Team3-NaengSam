package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 배치 매칭 사이클을 "변화가 있을 때만, window 당 한 번만" 실행하도록 예약한다. WAITING 방이 새로 생기는 등 배치가 필요한 변화가 생기면
 * {@link #markDirty()}를 호출한다. 이미 예약된 실행이 있으면(같은 window) 추가로 예약하지 않고, 배치 사이클이 실제로 실행될 때
 * {@link #reset()}을 호출해 다음 dirty 이벤트가 새 window를 열 수 있게 한다.
 *
 * <p>{@link #markDirty()}/{@link #reset()}은 항상 {@link MatchingEngine}의 단일 소비 스레드에서만 호출된다
 * (각각 {@code apply*} Action 안에서만 호출됨). 여러 스레드가 동시에 부를 수 있는 상태가 아니므로, 상태 등록과 markDirty
 * 호출 순서까지 원자적으로 보장해줄 필요가 없어 {@code AtomicBoolean} 대신 단순 boolean을 쓴다.
 */
@Component
public class MatchingBatchDispatcher {

    private boolean scheduled = false;
    private final MatchingEngine matchingEngine;
    private final MatchingPolicyProperties matchingPolicyProperties;
    private final MatchingService matchingService;

    public MatchingBatchDispatcher(MatchingEngine matchingEngine,
            MatchingPolicyProperties matchingPolicyProperties, @Lazy MatchingService matchingService) {
        this.matchingEngine = matchingEngine;
        this.matchingPolicyProperties = matchingPolicyProperties;
        this.matchingService = matchingService;
    }

    void markDirty() {
        if (!scheduled) {
            scheduled = true;
            matchingEngine.schedule(new RunMatchingAssignmentCycle(matchingService),
                    matchingPolicyProperties.batchWindow());
        }
    }

    void reset() {
        scheduled = false;
    }
}
