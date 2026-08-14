package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 웹푸시 문구에 대상(물품명)을 덧붙이는 규칙을 고정한다. */
class ChannelPlanTest {

    private static ChannelPlan plan() {
        return ChannelPlan.inAppAndWebPush("배달이 완료됐어요", "앱에서 배달 결과를 확인해주세요", Duration.ofHours(1));
    }

    @Test
    void 대상을_주면_제목_앞에_붙는다() {
        ChannelPlan withSubject = plan().withPushSubject("설계도면");

        assertThat(withSubject.pushTitle()).isEqualTo("'설계도면' 배달이 완료됐어요");
        // 본문·TTL·채널은 그대로 유지된다.
        assertThat(withSubject.pushBody()).isEqualTo("앱에서 배달 결과를 확인해주세요");
        assertThat(withSubject.pushTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(withSubject.channels()).isEqualTo(plan().channels());
    }

    @Test
    void 대상이_없거나_공백이면_기본_문구를_그대로_쓴다() {
        assertThat(plan().withPushSubject(null).pushTitle()).isEqualTo("배달이 완료됐어요");
        assertThat(plan().withPushSubject("   ").pushTitle()).isEqualTo("배달이 완료됐어요");
    }

    @Test
    void 인앱_전용_계획은_붙일_제목이_없어_그대로_돌려준다() {
        ChannelPlan inAppOnly = ChannelPlan.inAppOnly();

        assertThat(inAppOnly.withPushSubject("설계도면")).isEqualTo(inAppOnly);
    }
}
