package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.global.sse.SseEventType;
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
    void 등록되지_않은_이벤트는_IN_APP으로만_전달한다() {
        ChannelPlan plan = policy.planFor(() -> "unknown_event");

        assertThat(plan.channels()).containsExactly(NotificationChannel.IN_APP);
    }
}
