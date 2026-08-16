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

    /**
     * 알림 제목 앞에 대상(물품명 등)을 붙인 계획을 돌려준다. 잠금화면에서 "무엇에 대한 알림인지"를 앱을 열지 않고
     * 알 수 있게 하려는 것이며, 본문이 아니라 제목에 붙이는 이유는 본문이 기기·알림 개수에 따라 잘리기 때문이다.
     *
     * <p><b>아무 알림에나 쓰면 안 된다.</b> 이미 배달이 성사된 뒤의 진행 알림(픽업 완료·배달 완료)에만 쓴다.
     * 매칭 단계의 오퍼 알림은 아직 내 배달이 아닌 남의 주문 정보라, 제목에 물품명을 넣으면 수락하지도 않은
     * 사람들의 잠금화면에 주문 내용이 뿌려진다({@link com.naengsam.quick.global.notification.dto.PushEnvelope} 참고).
     *
     * @param subject 붙일 대상. null이거나 공백이면 원래 계획을 그대로 돌려준다.
     */
    public ChannelPlan withPushSubject(String subject) {
        if (subject == null || subject.isBlank() || pushTitle == null) {
            return this;
        }
        return new ChannelPlan(channels, "'%s' %s".formatted(subject.strip(), pushTitle), pushBody, pushTtl);
    }

    public boolean includes(NotificationChannel channel) {
        return channels.contains(channel);
    }
}
