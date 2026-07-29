package com.naengsam.quick.domain.user.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬/개발용 문자 발송기. 실제 발송 없이 인증번호를 로그로만 남긴다. {@code solapi.enabled} 가 없거나 false 일 때 활성화된다(크레덴셜 불필요).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "solapi.enabled", havingValue = "false", matchIfMissing = true)
public class DevSmsSender implements SmsSender {

    @Override
    public void send(String toPhone, String text) {
        log.info("[DEV-SMS] to={} text={}", toPhone, text);
    }
}
