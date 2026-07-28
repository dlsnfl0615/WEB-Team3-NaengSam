package com.naengsam.quick.domain.user.sms;

import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SOLAPI 기반 실제 문자 발송기. {@code solapi.enabled=true} 일 때만 빈으로 등록된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "solapi.enabled", havingValue = "true")
public class SolapiSmsSender implements SmsSender {

    private final SolapiProperties properties;
    private DefaultMessageService messageService;

    @PostConstruct
    void init() {
        messageService = SolapiClient.INSTANCE.createInstance(
                properties.apiKey(),
                properties.apiSecret());
    }

    @Override
    public void send(String toPhone, String text) {
        Message message = new Message();
        message.setFrom(properties.from());
        message.setTo(toPhone);
        message.setText(text);

        try {
            messageService.send(message);
        } catch (Exception e) {
            log.error("SOLAPI 문자 발송 실패 to={}", toPhone, e);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
