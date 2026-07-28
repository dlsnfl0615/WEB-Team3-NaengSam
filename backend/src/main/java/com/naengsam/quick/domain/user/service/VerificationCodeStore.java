package com.naengsam.quick.domain.user.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 인증번호 인메모리 저장소. 단일 인스턴스 전제(현재 세션 방식과 동일 철학)로, 만료는 접근 시 lazy 로 판단한다. 재시작 시 초기화되며 수평 확장에는 적합하지 않다(알려진 한계).
 */
@Component
public class VerificationCodeStore {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    public enum VerifyResult {
        OK, MISMATCH, EXPIRED
    }

    private record CodeEntry(String code, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> verified = new ConcurrentHashMap<>();

    /**
     * 재발송 쿨다운(마지막 발송 후 {@link #RESEND_COOLDOWN}) 경과 여부.
     */
    public boolean canResend(String phone) {
        CodeEntry entry = codes.get(phone);
        return entry == null
                || Duration.between(entry.lastSentAt(), LocalDateTime.now()).compareTo(RESEND_COOLDOWN) >= 0;
    }

    /**
     * 6자리 인증번호를 새로 발급해 저장하고 반환한다(TTL·발송시각 갱신).
     */
    public String issue(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        codes.put(phone, new CodeEntry(code, now.plus(CODE_TTL), now));
        return code;
    }

    /**
     * 인증번호를 검증한다. 성공 시 코드를 제거하고 인증완료 상태를 기록한다(1회용).
     */
    public VerifyResult verify(String phone, String code) {
        CodeEntry entry = codes.get(phone);
        if (entry == null || LocalDateTime.now().isAfter(entry.expiresAt())) {
            codes.remove(phone);
            return VerifyResult.EXPIRED;
        }
        if (!entry.code().equals(code)) {
            return VerifyResult.MISMATCH;
        }
        codes.remove(phone);
        verified.put(phone, LocalDateTime.now().plus(VERIFIED_TTL));
        return VerifyResult.OK;
    }

    /**
     * 인증완료 상태이며 아직 유효기간({@link #VERIFIED_TTL}) 내인지.
     */
    public boolean isVerified(String phone) {
        LocalDateTime expiry = verified.get(phone);
        if (expiry == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(expiry)) {
            verified.remove(phone);
            return false;
        }
        return true;
    }

    /**
     * 인증완료 상태를 소비한다(가입 완료 후 1회용 처리).
     */
    public void consumeVerified(String phone) {
        verified.remove(phone);
    }
}
