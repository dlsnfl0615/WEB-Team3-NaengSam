package com.naengsam.quick.global.notification;

import java.time.Duration;
import java.util.Set;

/**
 * 이벤트 하나를 어떤 채널과 웹푸시 문구로 전달할지 나타낸 불변 계획.
 */
public record ChannelPlan(
        Set<NotificationChannel> channels, // 인앱, 웹푸시 등 알림을 전달할 채널. 비어있으면 알림을 보내지 않는다.
        String pushTitle,
        String pushBody,
        Duration pushTtl
) {
    public ChannelPlan {
        channels = Set.copyOf(channels);
    }

    public static ChannelPlan inAppOnly() {
        return new ChannelPlan(Set.of(NotificationChannel.IN_APP), null, null, null);
    }

    public static ChannelPlan inAppAndWebPush(String pushTitle, String pushBody, Duration pushTtl) {
        return new ChannelPlan(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.WEB_PUSH),
                pushTitle,
                pushBody,
                pushTtl);
    }

    public boolean includes(NotificationChannel channel) {
        return channels.contains(channel);
    }
}
