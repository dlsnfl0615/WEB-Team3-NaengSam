package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.global.sse.SseEventType;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 알림 채널 결정표가 모든 현재 이벤트를 안전하게 분류하는지 검증한다. */
class NotificationPolicyTest {

    private final NotificationPolicy policy = new NotificationPolicy();

    @Test
    void 모든_매칭_배달_이벤트가_채널_결정표에_등록되어_있다() {
        Stream<SseEventType> allEventTypes = Stream.concat(
                Arrays.stream(MatchingEventType.values()),
                Arrays.stream(DeliveryEventType.values()));

        assertThat(allEventTypes).allMatch(policy::hasExplicitPlan);
    }

    @Test
    void delivery_location은_절대_WEB_PUSH_채널을_포함하지_않는다() {
        ChannelPlan plan = policy.planFor(DeliveryEventType.DELIVERY_LOCATION);

        assertThat(plan.includes(NotificationChannel.IN_APP)).isTrue();
        assertThat(plan.includes(NotificationChannel.WEB_PUSH)).isFalse();
    }

    @Test
    void 드리미_무소식은_부르미에게_인앱으로만_드리미에게는_웹푸시로_전달한다() {
        ChannelPlan toBoormi = policy.planFor(DeliveryEventType.DELIVERY_DREAMI_OFFLINE);
        ChannelPlan toDreami = policy.planFor(DeliveryEventType.DELIVERY_DREAMI_OFFLINE_SELF);

        // 부르미는 추적 화면을 보고 있다. 여기에 푸시를 켜면 손쓸 수 없는 지연을 잠금화면까지 밀어 넣는 셈이다.
        assertThat(toBoormi.includes(NotificationChannel.WEB_PUSH)).isFalse();
        // 드리미는 앱이 백그라운드거나 죽어서 무소식인 것이므로, 웹푸시가 빠지면 이 알림은 정의상 닿지 않는다.
        assertThat(toDreami.includes(NotificationChannel.WEB_PUSH)).isTrue();
        assertThat(toDreami.pushTtl()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 등록되지_않은_이벤트는_IN_APP으로만_전달한다() {
        ChannelPlan plan = policy.planFor(() -> "unknown_event");

        assertThat(plan.channels()).containsExactly(NotificationChannel.IN_APP);
    }
}
