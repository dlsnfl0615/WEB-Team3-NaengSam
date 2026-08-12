package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.sse.SseEventType;
import com.naengsam.quick.global.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 도메인이 사용자 알림을 보낼 때 사용하는 유일한 진입점.
 *
 * <p><b>스레드 제약:</b> {@link #notify}는 매칭 엔진의 단일 writer 스레드에서도 호출된다. 따라서 이 클래스에서
 * FCM·SOLAPI 같은 블로킹 외부 I/O를 호출 스레드에 동기 실행하면 안 된다. 현재 IN_APP 채널은 기존
 * {@link SseService}의 단일 sender 스레드로 위임하며, WEB_PUSH sender와 전용 executor는 해당 채널을 실제로
 * 연결하는 단계에서 추가한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationService {

    private final SseService sseService;
    private final NotificationPolicy policy;

    /**
     * 기존 {@link SseService#send}와 같은 인자로 알림 정책을 적용해 전달한다.
     * 웹푸시나 SSE(토스트) 중에서 어떤걸로 알릴지는 ChannelPlan에서 정의
     */
    public void notify(UUID userId, SseEventType eventType, Object payload) {
        ChannelPlan plan = policy.planFor(eventType);
        if (plan.includes(NotificationChannel.IN_APP)) {
            sseService.send(userId, eventType, payload);
        }
        // WEB_PUSH 실행은 구독 저장소와 sender가 함께 들어오는 Phase 5a에서 이 정책 분기에 연결한다.
    }

    /**
     * 현재 이 사용자에게 인앱 실시간 채널로 도달할 수 있는지 반환한다.
     */
    public boolean isReachableNow(UUID userId) {
        return sseService.isConnected(userId);
    }
}
