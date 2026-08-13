package com.naengsam.quick.global.notification;

import com.naengsam.quick.domain.user.sms.SmsSender;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 유료 채널(SMS) 발송기.
 *
 * <p><b>SMS는 {@link NotificationChannel}이 아니다.</b> 트리거가 "배달 중 드리미 장시간 무소식" 하나뿐이라
 * 파사드의 채널로 만들 이유가 없고, 무엇보다 {@link NotificationPolicy} 결정표에 SMS 항목이 없다는 것이
 * 기본값보다 강한 안전장치다 — 어떤 이벤트도 실수로 유료 채널에 배선될 <b>경로 자체가 존재하지 않는다.</b>
 * ({@code offer_popup}을 SMS 에 잘못 연결하면 오퍼 3개/라운드 × 약 3라운드 × 1,000주문/일 ≈ 9,000건/일이다.)
 *
 * <p>발송기는 기존 {@link SmsSender}를 그대로 재사용한다. 따라서 {@code solapi.enabled=false} 인 로컬에서는
 * {@code DevSmsSender}가 주입되어 문자 대신 로그만 남고, 크레덴셜 없이 전체 흐름을 확인할 수 있다.
 *
 * <p><b>레이트 리밋을 새로 만들지 않는다.</b> 배달별 영속 중복제거({@code DELIVERY.offline_sms_sent_dtm})가
 * 곧 레이트 리밋이라 배달당 최대 1건이다. 인증번호용 {@code SmsSendRateLimiter}를 공유하면 안내 문자를 받은
 * 라이더가 번호 재인증에서 잠기는 계정복구 버그가 된다.
 */
@Slf4j
@Component
public class SmsFallbackNotifier {

    /**
     * SOLAPI 단문 SMS 는 90바이트 상한이고 한글은 2바이트/자라, {@code [쉼,부름]} 접두어를 포함해 실질 예산이
     * ~45자다. 넘으면 조용히 LMS 로 승격되어 요금이 약 3배가 된다(현재 73바이트).
     *
     * <p>URL 을 넣지 않는다 — 바이트 예산을 먹고, 미등록 발신자의 링크 문자를 국내 통신사가 스팸으로 취급한다.
     * 정보성 메시지라 {@code (광고)} 접두어·수신거부 문구가 필요 없어 이 예산이 달성 가능하다.
     */
    static final String DREAMI_OFFLINE_TEXT = "[쉼,부름] 진행 중인 배달이 멈췄어요. 앱을 다시 열어 배달을 이어가 주세요.";

    private final SmsSender smsSender;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public SmsFallbackNotifier(SmsSender smsSender, MeterRegistry meterRegistry,
            @Value("${notification.sms-fallback-enabled}") boolean enabled) {
        this.smsSender = smsSender;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    /**
     * 킬 스위치 상태. 호출자는 이 값이 false 면 중복제거 컬럼을 찍지 않아야 한다 — 껐던 기간의 배달이 플래그를
     * 켠 뒤에도 영구히 알림을 못 받게 되기 때문이다.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 드리미 본인에게 배달이 멈췄다는 안내 문자를 보낸다.
     *
     * <p>실패를 호출자로 전파하지 않는다. 이 문자는 이미 인앱·웹푸시가 닿지 않은 뒤의 마지막 보조 채널이라,
     * 여기서 예외를 던지면 5초마다 도는 감지 스캔 전체가 흔들린다. 재시도도 하지 않는다(호출자가 시도 시점을
     * 영속 기록하므로) — 유료 채널에서는 중복 발송이 미발송보다 나쁘다.
     */
    public void sendDreamiOffline(String toPhone) {
        if (!enabled) {
            return;
        }
        try {
            smsSender.send(toPhone, DREAMI_OFFLINE_TEXT);
            meterRegistry.counter("sms.sent", "trigger", "dreami_offline").increment();
        } catch (Exception e) {
            log.warn("드리미 무소식 안내 문자 발송 실패, 재시도하지 않는다: reason={}", e.toString());
            meterRegistry.counter("notification.dropped", "channel", "sms", "reason", "send_failed").increment();
        }
    }
}
