package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.service.VerificationCodeStore.VerifyResult;
import com.naengsam.quick.domain.user.sms.SmsSender;
import com.naengsam.quick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 휴대폰 인증(발송/검증) 오케스트레이션. 인증번호 저장소와 문자 발송기를 조합하고 재발송 쿨다운을 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private final VerificationCodeStore store;
    private final SmsSendRateLimiter rateLimiter;
    private final SmsSender smsSender;

    /**
     * 인증번호를 발급해 문자로 발송한다. 쿨다운 이내 재요청, 그리고 번호/전역 발송 한도 초과 요청은 거부한다.
     */
    public void send(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!store.canResend(phone)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_REQUEST_EXCEEDED);
        }
        // 어떤 한도에 걸렸는지는 노출하지 않는다(정보 누출 방지) — 동일한 429 로 응답한다.
        if (!rateLimiter.tryAcquire(phone)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_REQUEST_EXCEEDED);
        }
        String code = store.issue(phone);
        smsSender.send(phone, "[쉼,부름] 인증번호 [" + code + "]를 입력해 주세요.");
    }

    /**
     * 인증번호를 검증한다. 성공 시 해당 번호는 인증완료 상태가 된다.
     */
    public void verify(String rawPhone, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        VerifyResult result = store.verify(phone, code);
        switch (result) {
            case EXPIRED -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
            case MISMATCH -> throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
            case TOO_MANY_ATTEMPTS -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_ATTEMPTS_EXCEEDED);
            case OK -> {
                // 인증완료 상태는 store 에 기록됨
            }
        }
    }

    public boolean isVerified(String rawPhone) {
        return store.isVerified(PhoneNumbers.normalize(rawPhone));
    }

    public void consumeVerified(String rawPhone) {
        store.consumeVerified(PhoneNumbers.normalize(rawPhone));
    }
}
