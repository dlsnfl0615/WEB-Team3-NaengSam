package com.naengsam.quick.global.notification;

import com.naengsam.quick.domain.user.sms.SmsSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 유료 SMS 발송기 단위 테스트. 킬 스위치, 문구 바이트 예산, 그리고 실패가 호출자로 전파되지 않는지를 확인한다.
 */
class SmsFallbackNotifierTest {

    private final SmsSender smsSender = mock(SmsSender.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void 킬_스위치가_켜져_있으면_문자를_발송하고_지표를_올린다() {
        SmsFallbackNotifier notifier = notifier(true);

        notifier.sendDreamiOffline("01012345678");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(eq("01012345678"), text.capture());
        assertThat(text.getValue()).isEqualTo(SmsFallbackNotifier.DREAMI_OFFLINE_TEXT);
        assertThat(meterRegistry.counter("sms.sent", "trigger", "dreami_offline").count()).isEqualTo(1);
    }

    @Test
    void 킬_스위치가_꺼져_있으면_아무것도_발송하지_않는다() {
        SmsFallbackNotifier notifier = notifier(false);

        notifier.sendDreamiOffline("01012345678");

        verify(smsSender, never()).send(anyString(), anyString());
        assertThat(notifier.isEnabled()).isFalse();
    }

    /** 이미 인앱·웹푸시가 닿지 않은 뒤의 마지막 보조 채널이라, 실패가 5초 감지 스캔을 흔들면 안 된다. */
    @Test
    void 발송이_실패해도_예외를_전파하지_않고_지표만_올린다() {
        SmsFallbackNotifier notifier = notifier(true);
        willThrow(new RuntimeException("SOLAPI down")).given(smsSender).send(anyString(), anyString());

        assertThatCode(() -> notifier.sendDreamiOffline("01012345678")).doesNotThrowAnyException();

        assertThat(meterRegistry.counter("notification.dropped", "channel", "sms", "reason", "send_failed").count())
                .isEqualTo(1);
    }

    /**
     * SOLAPI 단문 SMS 는 90바이트 상한이고, 넘으면 조용히 LMS 로 승격되어 요금이 약 3배가 된다.
     * 문구를 고칠 때 이 테스트가 예산 초과를 잡는다(한글 2바이트 인코딩 기준으로 EUC-KR 로 센다).
     */
    @Test
    void 안내_문구는_단문_SMS_90바이트_예산_안에_들어간다() {
        int bytes = SmsFallbackNotifier.DREAMI_OFFLINE_TEXT.getBytes(Charset.forName("EUC-KR")).length;

        assertThat(bytes).isLessThanOrEqualTo(90);
    }

    /** URL 은 바이트 예산을 먹고, 미등록 발신자의 링크 문자를 국내 통신사가 스팸으로 취급한다. */
    @Test
    void 안내_문구에는_URL_을_넣지_않는다() {
        assertThat(SmsFallbackNotifier.DREAMI_OFFLINE_TEXT).doesNotContain("http");
    }

    private SmsFallbackNotifier notifier(boolean enabled) {
        return new SmsFallbackNotifier(smsSender, meterRegistry, enabled);
    }
}
