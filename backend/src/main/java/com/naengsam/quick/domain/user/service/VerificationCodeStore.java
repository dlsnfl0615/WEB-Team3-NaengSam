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

    public enum VerifyResult {
        OK, MISMATCH, EXPIRED, TOO_MANY_ATTEMPTS
    }

    private record CodeEntry(String code, LocalDateTime expiresAt, LocalDateTime lastSentAt, int attempts) {
    }

    private final VerificationProperties props;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> verified = new ConcurrentHashMap<>();

    public VerificationCodeStore(VerificationProperties props) {
        this.props = props;
    }

    /**
     * 재발송 쿨다운(마지막 발송 후 {@code resendCooldown}) 경과 여부.
     */
    public boolean canResend(String phone) {
        CodeEntry entry = codes.get(phone);
        return entry == null
                || Duration.between(entry.lastSentAt(), LocalDateTime.now()).compareTo(props.resendCooldown()) >= 0;
    }

    /**
     * 6자리 인증번호를 새로 발급해 저장하고 반환한다(TTL·발송시각 갱신, 시도 횟수 초기화).
     */
    public String issue(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        codes.put(phone, new CodeEntry(code, now.plus(props.codeTtl()), now, 0));
        return code;
    }

    /**
     * 인증번호를 검증한다. 성공 시 코드를 제거하고 인증완료 상태를 기록한다(1회용). 불일치가 누적되어
     * {@code maxVerifyAttempts} 에 도달하면 코드를 폐기해 무한 대입(brute-force)을 차단한다.
     *
     * <p>검증·시도횟수 갱신은 {@link ConcurrentHashMap#compute}로 원자적으로 수행한다.
     */
    public VerifyResult verify(String phone, String code) {
        VerifyResult[] result = {VerifyResult.EXPIRED};
        codes.compute(phone, (key, entry) -> {
            if (entry == null || LocalDateTime.now().isAfter(entry.expiresAt())) {
                result[0] = VerifyResult.EXPIRED;
                return null;
            }
            if (entry.code().equals(code)) {
                result[0] = VerifyResult.OK;
                return null;
            }
            int attempts = entry.attempts() + 1;
            if (attempts >= props.maxVerifyAttempts()) {
                result[0] = VerifyResult.TOO_MANY_ATTEMPTS;
                return null;
            }
            result[0] = VerifyResult.MISMATCH;
            return new CodeEntry(entry.code(), entry.expiresAt(), entry.lastSentAt(), attempts);
        });
        if (result[0] == VerifyResult.OK) {
            verified.put(phone, LocalDateTime.now().plus(props.verifiedTtl()));
        }
        return result[0];
    }

    /**
     * 인증완료 상태이며 아직 유효기간({@code verifiedTtl}) 내인지.
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
