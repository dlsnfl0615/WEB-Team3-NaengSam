package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * WAITING 방이 생겨도 배치 매칭 사이클을 window당 한 번만 예약하는지 검증한다.
 */
class MatchingBatchDispatcherTest {

    private MatchingBatchDispatcher matchingBatchDispatcher;
    private MatchingActionScheduler matchingActionScheduler;
    private Duration batchWindow;

    @BeforeEach
    void setUp() {
        matchingActionScheduler = mock(MatchingActionScheduler.class);
        MatchingPolicyProperties matchingPolicyProperties = mock(MatchingPolicyProperties.class);
        MatchingService matchingService = mock(MatchingService.class);
        batchWindow = Duration.ofMillis(200);
        when(matchingPolicyProperties.batchWindow()).thenReturn(batchWindow);
        matchingBatchDispatcher =
                new MatchingBatchDispatcher(matchingActionScheduler, matchingPolicyProperties, matchingService);
    }

    @Test
    void 첫_markDirty는_배치_사이클_실행을_한번_예약한다() {
        // when
        matchingBatchDispatcher.markDirty();

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingActionScheduler).schedule(captor.capture(), eq(batchWindow));
        assertThat(captor.getValue()).isInstanceOf(RunMatchingAssignmentCycle.class);
    }

    @Test
    void 같은_window_안에서_추가_markDirty는_다시_예약하지_않는다() {
        // when
        matchingBatchDispatcher.markDirty();
        matchingBatchDispatcher.markDirty();

        // then
        verify(matchingActionScheduler, times(1)).schedule(any(), eq(batchWindow));
    }

    @Test
    void reset_후_markDirty는_새_window를_다시_예약한다() {
        // when
        matchingBatchDispatcher.markDirty();
        matchingBatchDispatcher.reset();
        matchingBatchDispatcher.markDirty();

        // then
        verify(matchingActionScheduler, times(2)).schedule(any(), eq(batchWindow));
    }
}
