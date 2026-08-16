package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.sse.SseEventType;
import com.naengsam.quick.global.sse.SseService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 알림 파사드가 정책에 따라 인앱·웹푸시 채널을 갈라 보내는지 검증한다. */
class NotificationServiceTest {

    private static final ChannelPlan PUSH_PLAN = new ChannelPlan(
            Set.of(NotificationChannel.IN_APP, NotificationChannel.WEB_PUSH),
            "제목", "본문", Duration.ofSeconds(30));

    private SseService sseService;
    private NotificationPolicy policy;
    private PushSubscriptionService pushSubscriptionService;
    private WebPushSender webPushSender;

    @BeforeEach
    void setUp() {
        sseService = mock(SseService.class);
        policy = mock(NotificationPolicy.class);
        pushSubscriptionService = mock(PushSubscriptionService.class);
        webPushSender = mock(WebPushSender.class);
    }

    /**
     * @param sender null이면 {@code web-push.enabled=false}(빈 미등록) 상태를 재현한다.
     */
    private NotificationService notificationServiceWith(WebPushSender sender) {
        @SuppressWarnings("unchecked")
        ObjectProvider<WebPushSender> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(sender);
        return new NotificationService(
                sseService, policy, provider, pushSubscriptionService, new SimpleMeterRegistry());
    }

    @Test
    void IN_APP_계획이면_기존_SseService에_인자를_그대로_전달한다() {
        UUID userId = UUID.randomUUID();
        SseEventType eventType = () -> "test_event";
        Object payload = new Object();
        given(policy.planFor(eventType)).willReturn(ChannelPlan.inAppOnly());

        notificationServiceWith(null).notify(userId, eventType, payload);

        verify(sseService).send(userId, eventType, payload);
    }

    @Test
    void IN_APP이_없는_계획이면_SseService를_호출하지_않는다() {
        SseEventType eventType = () -> "push_only";
        ChannelPlan pushOnly = new ChannelPlan(
                Set.of(NotificationChannel.WEB_PUSH), "제목", "본문", Duration.ofSeconds(30));
        given(policy.planFor(eventType)).willReturn(pushOnly);

        notificationServiceWith(null).notify(UUID.randomUUID(), eventType, new Object());

        verify(sseService, never()).send(any(), any(), any());
    }

    @Test
    void isReachableNow는_SSE_연결_상태를_그대로_반환한다() {
        UUID userId = UUID.randomUUID();
        given(sseService.isConnected(userId)).willReturn(true);

        boolean reachable = notificationServiceWith(null).isReachableNow(userId);

        assertThat(reachable).isTrue();
        verify(sseService).isConnected(userId);
    }

    @Test
    void 웹푸시가_비활성이면_구독을_조회하지_않고_인앱만_보낸다() {
        UUID userId = UUID.randomUUID();
        SseEventType eventType = () -> "offer_popup";
        given(policy.planFor(eventType)).willReturn(PUSH_PLAN);

        notificationServiceWith(null).notify(userId, eventType, new Object());

        verify(sseService).send(eq(userId), eq(eventType), any());
        verify(pushSubscriptionService, never()).findAllFor(any());
    }

    @Test
    void WEB_PUSH_계획이면_사용자의_모든_구독에_보내고_결과를_반영한다() {
        UUID userId = UUID.randomUUID();
        SseEventType eventType = () -> "offer_popup";
        PushSubscription subscription = PushSubscription.create(
                userId, "https://push.example/abc", "key", "auth", "ua");
        given(policy.planFor(eventType)).willReturn(PUSH_PLAN);
        given(pushSubscriptionService.findAllFor(userId)).willReturn(List.of(subscription));
        given(webPushSender.send(subscription, PUSH_PLAN, "offer_popup"))
                .willReturn(PushSendOutcome.SUCCESS);

        notificationServiceWith(webPushSender).notify(userId, eventType, new Object());

        // 전송은 outbound executor로 오프로드되므로(매칭 엔진 스레드를 막지 않기 위해) 완료를 기다린다.
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                verify(pushSubscriptionService).applyOutcome(subscription.getEndpoint(), PushSendOutcome.SUCCESS));
    }

    @Test
    void 웹푸시_전송이_실패해도_호출자에게_예외가_전파되지_않는다() {
        UUID userId = UUID.randomUUID();
        SseEventType eventType = () -> "offer_popup";
        given(policy.planFor(eventType)).willReturn(PUSH_PLAN);
        given(pushSubscriptionService.findAllFor(userId)).willThrow(new IllegalStateException("DB 장애"));

        NotificationService notificationService = notificationServiceWith(webPushSender);

        // 인앱 알림은 이미 나갔고, 보조 채널의 장애가 도메인 로직을 흔들면 안 된다.
        notificationService.notify(userId, eventType, new Object());

        verify(sseService).send(eq(userId), eq(eventType), any());
    }
}
