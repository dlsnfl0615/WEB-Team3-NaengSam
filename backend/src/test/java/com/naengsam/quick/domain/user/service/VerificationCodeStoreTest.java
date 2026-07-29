package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.user.service.VerificationCodeStore.VerifyResult;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 인증번호 인메모리 상태머신 단위 테스트. 발급/검증/인증완료/소비 상태 전이를 공개 API 흐름으로 검증한다.
 * 시간 만료가 필요한 케이스는 내부 verified 맵에 과거 시각을 주입해 확인한다.
 */
class VerificationCodeStoreTest {

    private static final String PHONE = "01012345678";

    private VerificationCodeStore store;

    @BeforeEach
    void setUp() {
        store = new VerificationCodeStore();
    }

    @Test
    void issue_는_6자리_숫자_코드를_발급한다() {
        String code = store.issue(PHONE);

        assertThat(code).matches("\\d{6}");
    }

    @Test
    void issue_직후에는_재발송_쿨다운에_걸린다() {
        store.issue(PHONE);

        assertThat(store.canResend(PHONE)).isFalse();
    }

    @Test
    void 발급된_적_없는_번호는_재발송이_가능하다() {
        assertThat(store.canResend(PHONE)).isTrue();
    }

    @Test
    void 정답_코드로_검증하면_OK_이고_인증완료_상태가_된다() {
        String code = store.issue(PHONE);

        VerifyResult result = store.verify(PHONE, code);

        assertThat(result).isEqualTo(VerifyResult.OK);
        assertThat(store.isVerified(PHONE)).isTrue();
    }

    @Test
    void 오답_코드로_검증하면_MISMATCH_이고_인증완료가_아니다() {
        store.issue(PHONE);

        VerifyResult result = store.verify(PHONE, "000000");

        assertThat(result).isEqualTo(VerifyResult.MISMATCH);
        assertThat(store.isVerified(PHONE)).isFalse();
    }

    @Test
    void 발급된_적_없는_번호_검증은_EXPIRED_이다() {
        VerifyResult result = store.verify(PHONE, "123456");

        assertThat(result).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    void 검증_성공한_코드는_1회용이라_재검증하면_EXPIRED_이다() {
        String code = store.issue(PHONE);
        store.verify(PHONE, code);

        VerifyResult result = store.verify(PHONE, code);

        assertThat(result).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    void consumeVerified_후에는_인증완료가_해제된다() {
        String code = store.issue(PHONE);
        store.verify(PHONE, code);

        store.consumeVerified(PHONE);

        assertThat(store.isVerified(PHONE)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 인증완료_유효기간이_지나면_isVerified_는_false_이고_상태가_제거된다() {
        ConcurrentHashMap<String, LocalDateTime> verified =
                (ConcurrentHashMap<String, LocalDateTime>) ReflectionTestUtils.getField(store, "verified");
        verified.put(PHONE, LocalDateTime.now().minusMinutes(1));

        assertThat(store.isVerified(PHONE)).isFalse();
        assertThat(verified).doesNotContainKey(PHONE);
    }
}
