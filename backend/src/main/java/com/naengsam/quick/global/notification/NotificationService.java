package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.debug.InMemoryStateProbe;
import com.naengsam.quick.global.debug.InMemoryStructureDto;
import com.naengsam.quick.global.sse.SseEventType;
import com.naengsam.quick.global.sse.SseService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 도메인이 사용자 알림을 보낼 때 사용하는 유일한 진입점.
 *
 * <p><b>스레드 제약:</b> {@link #notify}는 매칭 엔진의 단일 writer 스레드에서도 호출된다. 따라서 이 클래스에서
 * 블로킹 외부 I/O를 호출 스레드에 동기 실행하면 안 된다. IN_APP은 기존 {@link SseService}의 단일 sender 스레드로
 * 위임하고(사용자별 이벤트 순서가 보존되어야 하므로 이 경로는 절대 바꾸지 않는다), WEB_PUSH는 아래 outbound
 * executor로 오프로드한다.
 */
@Slf4j
@Component
public class NotificationService implements InMemoryStateProbe {

    /**
     * 유계 병렬성. 가상 스레드는 블로킹 HTTP 중 unmount되므로 4개로도 충분하고, 오퍼가 몰려도 스레드가 폭발하지 않는다.
     * {@code newVirtualThreadPerTaskExecutor()}는 무제한이라 푸시 서비스 장애 시 태스크가 무한히 쌓여 쓰지 않는다.
     */
    private static final int OUTBOUND_THREADS = 4;

    /**
     * 큐도 유계로 둔다. 푸시 서비스가 죽으면 태스크가 HTTP 타임아웃만큼씩 밀리는데, 무한 큐는 단일 JVM의 메모리를
     * 잠식한다. 넘치면 버리는 편이 옳다 — 이 봉투들은 어차피 TTL이 짧고, 늦게 도착하는 wake-up은 없느니만 못하다.
     */
    private static final int OUTBOUND_QUEUE_CAPACITY = 1_000;

    private final SseService sseService;
    private final NotificationPolicy policy;
    private final ObjectProvider<WebPushSender> webPushSender;
    private final PushSubscriptionService pushSubscriptionService;
    private final MeterRegistry meterRegistry;
    private final ThreadPoolExecutor outbound;

    public NotificationService(
            SseService sseService,
            NotificationPolicy policy,
            ObjectProvider<WebPushSender> webPushSender,
            PushSubscriptionService pushSubscriptionService,
            MeterRegistry meterRegistry) {
        this.sseService = sseService;
        this.policy = policy;
        this.webPushSender = webPushSender;
        this.pushSubscriptionService = pushSubscriptionService;
        this.meterRegistry = meterRegistry;
        this.outbound = new ThreadPoolExecutor(
                OUTBOUND_THREADS, OUTBOUND_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY),
                r -> Thread.ofVirtual().name("notification-outbound-", 0).unstarted(r));

        // 큐가 차오르는 것이 곧 외부 푸시 서비스 장애 신호다. 기존 Grafana에서 바로 보이게 노출한다.
        Gauge.builder("notification.outbound.queue.size", outbound, e -> e.getQueue().size())
                .description("웹푸시 전송 대기 큐 길이")
                .register(meterRegistry);
    }

    /**
     * 기존 {@link SseService#send}와 같은 인자로 알림 정책을 적용해 전달한다. 어떤 채널을 쓸지는
     * {@link NotificationPolicy}의 결정표가 정한다.
     */
    public void notify(UUID userId, SseEventType eventType, Object payload) {
        notify(userId, eventType, payload, null);
    }

    /**
     * 웹푸시 제목에 대상(물품명 등)을 덧붙여 전달한다. 인앱(SSE) payload는 영향을 받지 않는다 —
     * 앱이 열려 있으면 화면이 이미 맥락을 알고 있고, 이 값은 잠금화면 문구를 위한 것이기 때문이다.
     *
     * @param pushSubject 제목 앞에 붙일 대상. null이면 정책의 기본 문구를 그대로 쓴다.
     *                    매칭 단계 알림에는 넘기지 않는다({@link ChannelPlan#withPushSubject}의 주의 참고).
     */
    public void notify(UUID userId, SseEventType eventType, Object payload, String pushSubject) {
        ChannelPlan plan = policy.planFor(eventType);
        if (plan.includes(NotificationChannel.IN_APP)) {
            sseService.send(userId, eventType, payload);
        }
        if (plan.includes(NotificationChannel.WEB_PUSH)) {
            submitWebPush(userId, eventType, plan.withPushSubject(pushSubject));
        }
    }

    /**
     * 현재 이 사용자에게 인앱 실시간 채널로 도달할 수 있는지 반환한다.
     *
     * <p>기준이 "푸시 구독 보유"가 아니라 "살아 있는 SSE 연결"인 것은 의도다. 푸시로 깨워도 30초짜리 오퍼에
     * 응답하는 것은 사실상 불가능하므로, 오퍼 후보 선정에는 실시간 연결만이 의미 있다.
     */
    public boolean isReachableNow(UUID userId) {
        return sseService.isConnected(userId);
    }

    private void submitWebPush(UUID userId, SseEventType eventType, ChannelPlan plan) {
        WebPushSender sender = webPushSender.getIfAvailable();
        if (sender == null) {
            return; // web-push.enabled=false — 채널을 통째로 건너뛴다.
        }
        try {
            outbound.execute(() -> sendPushQuietly(sender, userId, eventType.eventName(), plan));
        } catch (RejectedExecutionException e) {
            dropped("queue_full");
        }
    }

    /**
     * 푸시 실패는 절대 호출자로 전파하지 않는다. 인앱 알림은 이미 나갔고, 도메인 로직이 보조 채널의 장애로
     * 흔들리면 안 된다. 모든 실패는 지표와 요약 로그로만 남긴다.
     */
    private void sendPushQuietly(WebPushSender sender, UUID userId, String eventName, ChannelPlan plan) {
        try {
            List<PushSubscription> subscriptions = pushSubscriptionService.findAllFor(userId);
            for (PushSubscription subscription : subscriptions) {
                PushSendOutcome outcome = sender.send(subscription, plan, eventName);
                pushSubscriptionService.applyOutcome(subscription.getEndpoint(), outcome);
                if (outcome != PushSendOutcome.SUCCESS) {
                    dropped(outcome.metricReason());
                }
            }
        } catch (Exception e) {
            log.warn("웹푸시 전송 처리 실패, 무시: event={}, reason={}", eventName, e.toString());
            dropped("unexpected_error");
        }
    }

    private void dropped(String reason) {
        meterRegistry.counter("notification.dropped", "channel", "web_push", "reason", reason).increment();
    }

    /**
     * 웹푸시 전송 대기 큐의 현황. 이 큐는 {@code OUTBOUND_QUEUE_CAPACITY}로 유계라 무한히 자라지 않고 넘치면 버려지므로, 누수보다는 "큐가 차오르고 있다 = 외부 푸시 서비스가
     * 느리거나 죽었다"를 읽는 지표다.
     */
    @Override
    public List<InMemoryStructureDto> inMemoryStructures() {
        return List.of(InMemoryStructureDto
                .ofSize("outbound.queue", "웹푸시 전송 대기 태스크 (상한 " + OUTBOUND_QUEUE_CAPACITY + ")",
                        outbound.getQueue().size())
                .withBreakdown(Map.of(
                        "실행 중인 스레드", (long) outbound.getActiveCount(),
                        "완료 태스크", outbound.getCompletedTaskCount())));
    }

    @PreDestroy
    void shutdown() {
        outbound.shutdown();
    }
}
