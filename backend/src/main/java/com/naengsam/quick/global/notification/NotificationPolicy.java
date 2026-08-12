package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.sse.SseEventType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * SSE 와이어 이벤트 이름을 기준으로 사용할 알림 채널과 웹푸시 문구를 결정한다.
 */
@Component
public class NotificationPolicy {

    // 어떤 알림은 어떤 종류로 알림을 보낼지
    // 메시지(SOLAPI)는 여기서 관리하지 않음
    private static final Map<String, ChannelPlan> PLANS = Map.ofEntries(
            Map.entry("offer_popup", ChannelPlan.inAppAndWebPush(
                    "새 배달 요청이 왔어요", "앱을 열어 확인해주세요", Duration.ofSeconds(30))),
            Map.entry("offer_closed", ChannelPlan.inAppOnly()),
            Map.entry("dreami_info", ChannelPlan.inAppAndWebPush(
                    "드리미를 찾았어요", "앱을 열어 확정해주세요", Duration.ofSeconds(30))),
            Map.entry("boormi_rejected", ChannelPlan.inAppOnly()),
            Map.entry("offer_error", ChannelPlan.inAppOnly()),
            Map.entry("delivery_location", ChannelPlan.inAppOnly()),
            Map.entry("delivery_delivering", ChannelPlan.inAppAndWebPush(
                    "픽업이 완료됐어요", "물품이 도착지로 이동 중이에요", Duration.ofMinutes(10))),
            Map.entry("delivery_cancelled", ChannelPlan.inAppAndWebPush(
                    "배달이 취소됐어요", "앱에서 환불 내역을 확인해주세요", Duration.ofHours(1))),
            Map.entry("delivery_completed", ChannelPlan.inAppAndWebPush(
                    "배달이 완료됐어요", "앱에서 배달 결과를 확인해주세요", Duration.ofHours(1))),
            Map.entry("delivery_started_boormi", ChannelPlan.inAppAndWebPush(
                    "배달이 시작됐어요", "실시간으로 위치를 확인해보세요", Duration.ofMinutes(10))),
            Map.entry("delivery_started_dreami", ChannelPlan.inAppAndWebPush(
                    "배달이 시작됐어요", "픽업지로 이동해주세요", Duration.ofMinutes(10))),
            Map.entry("delivery_dreami_offline", ChannelPlan.inAppOnly())
    );

    /**
     * 등록되지 않은 새 이벤트는 실수로 외부 채널을 타지 않도록 인앱 전용으로 처리한다.
     */
    public ChannelPlan planFor(SseEventType eventType) {
        return PLANS.getOrDefault(eventType.eventName(), ChannelPlan.inAppOnly());
    }

    boolean hasExplicitPlan(SseEventType eventType) {
        return PLANS.containsKey(eventType.eventName());
    }
}
