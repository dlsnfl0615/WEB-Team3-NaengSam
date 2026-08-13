package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 애플리케이션 준비 후 배치 매칭 사이클이 {@code matching.batch-window} 주기로 반복 예약되는지 검증한다.
 */
class PeriodicMatchingBatchSchedulerTest {

    private MatchingEngine matchingEngine;
    private MatchingService matchingService;
    private PeriodicMatchingBatchScheduler scheduler;
    private Duration batchWindow;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        matchingService = mock(MatchingService.class);
        MatchingPolicyProperties matchingPolicyProperties = mock(MatchingPolicyProperties.class);
        batchWindow = Duration.ofSeconds(5);
        given(matchingPolicyProperties.batchWindow()).willReturn(batchWindow);
        scheduler = new PeriodicMatchingBatchScheduler(matchingEngine, matchingPolicyProperties, matchingService);
    }

    @Test
    void 애플리케이션_준비_이벤트가_오면_배치_사이클을_batchWindow_주기로_반복_예약한다() {
        scheduler.start();

        ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).scheduleRepeating(actionCaptor.capture(), any(Duration.class));
        assertThat(actionCaptor.getValue()).isEqualTo(new RunMatchingAssignmentCycle(matchingService));

        ArgumentCaptor<Duration> intervalCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(matchingEngine).scheduleRepeating(any(Action.class), intervalCaptor.capture());
        assertThat(intervalCaptor.getValue()).isEqualTo(batchWindow);
    }
}
