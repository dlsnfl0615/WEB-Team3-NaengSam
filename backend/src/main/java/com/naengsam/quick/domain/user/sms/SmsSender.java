package com.naengsam.quick.domain.user.sms;

/**
 * 문자 발송 추상화. 운영은 {@link SolapiSmsSender}, 로컬 개발은 {@link DevSmsSender} 가 주입된다.
 */
public interface SmsSender {

    void send(String toPhone, String text);
}
