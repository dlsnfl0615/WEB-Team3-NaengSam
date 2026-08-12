package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 푸시 서비스 응답 코드를 구독 처리 방침으로 옮기는 규칙을 고정한다. */
class PushSendOutcomeTest {

    @Test
    void 성공_계열_2xx는_모두_SUCCESS다() {
        assertThat(PushSendOutcome.fromStatusCode(200)).isEqualTo(PushSendOutcome.SUCCESS);
        assertThat(PushSendOutcome.fromStatusCode(201)).isEqualTo(PushSendOutcome.SUCCESS);
        assertThat(PushSendOutcome.fromStatusCode(204)).isEqualTo(PushSendOutcome.SUCCESS);
    }

    @Test
    void 구독이_사라진_404와_410만_삭제_대상이다() {
        assertThat(PushSendOutcome.fromStatusCode(404)).isEqualTo(PushSendOutcome.EXPIRED);
        assertThat(PushSendOutcome.fromStatusCode(410)).isEqualTo(PushSendOutcome.EXPIRED);
    }

    @Test
    void 설정_오류로_보이는_4xx는_REJECTED로_분류해_구독을_보존한다() {
        // VAPID 키 불일치(401/403)로 전체 구독이 삭제되는 사고를 막는 분기다.
        assertThat(PushSendOutcome.fromStatusCode(400)).isEqualTo(PushSendOutcome.REJECTED);
        assertThat(PushSendOutcome.fromStatusCode(401)).isEqualTo(PushSendOutcome.REJECTED);
        assertThat(PushSendOutcome.fromStatusCode(403)).isEqualTo(PushSendOutcome.REJECTED);
    }

    @Test
    void 레이트_리밋과_페이로드_과대는_각각_구분된다() {
        assertThat(PushSendOutcome.fromStatusCode(429)).isEqualTo(PushSendOutcome.RATE_LIMITED);
        assertThat(PushSendOutcome.fromStatusCode(413)).isEqualTo(PushSendOutcome.PAYLOAD_TOO_LARGE);
    }

    @Test
    void 서버_장애_5xx는_재시도_가능_실패로_센다() {
        assertThat(PushSendOutcome.fromStatusCode(500)).isEqualTo(PushSendOutcome.RETRIABLE_FAILURE);
        assertThat(PushSendOutcome.fromStatusCode(503)).isEqualTo(PushSendOutcome.RETRIABLE_FAILURE);
    }
}
