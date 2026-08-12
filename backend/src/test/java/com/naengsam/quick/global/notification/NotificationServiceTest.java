package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.sse.SseEventType;
import com.naengsam.quick.global.sse.SseService;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 알림 파사드가 정책에 따라 기존 인앱 채널을 그대로 위임하는지 검증한다. */
class NotificationServiceTest {

    private SseService sseService;
    private NotificationPolicy policy;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        sseService = mock(SseService.class);
        policy = mock(NotificationPolicy.class);
        notificationService = new NotificationService(sseService, policy);
    }

    @Test
    void IN_APP_계획이면_기존_SseService에_인자를_그대로_전달한다() {
        UUID userId = UUID.randomUUID();
        SseEventType eventType = () -> "test_event";
        Object payload = new Object();
        given(policy.planFor(eventType)).willReturn(ChannelPlan.inAppOnly());

        notificationService.notify(userId, eventType, payload);

        verify(sseService).send(userId, eventType, payload);
    }

    @Test
    void IN_APP이_없는_계획이면_SseService를_호출하지_않는다() {
        SseEventType eventType = () -> "push_only";
        ChannelPlan pushOnly = new ChannelPlan(
                Set.of(NotificationChannel.WEB_PUSH), "제목", "본문", Duration.ofSeconds(30));
        given(policy.planFor(eventType)).willReturn(pushOnly);

        notificationService.notify(UUID.randomUUID(), eventType, new Object());

        verify(sseService, never()).send(any(), any(), any());
    }

    @Test
    void isReachableNow는_SSE_연결_상태를_그대로_반환한다() {
        UUID userId = UUID.randomUUID();
        given(sseService.isConnected(userId)).willReturn(true);

        boolean reachable = notificationService.isReachableNow(userId);

        assertThat(reachable).isTrue();
        verify(sseService).isConnected(userId);
    }
}
