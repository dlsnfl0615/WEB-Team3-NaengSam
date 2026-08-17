package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.user.service.VerificationCodeStore.VerifyResult;
import com.naengsam.quick.global.debug.InMemoryStructureDto;
import java.time.Duration;
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
    private static final int MAX_ATTEMPTS = 5;

    private VerificationCodeStore store;

    @BeforeEach
    void setUp() {
        store = new VerificationCodeStore(props());
    }

    private static VerificationProperties props() {
        return new VerificationProperties(
                Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofMinutes(30),
                MAX_ATTEMPTS,
                Duration.ofHours(24), 5, Duration.ofHours(24), 1000);
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
    void 오답을_최대_시도횟수만큼_넣으면_마지막에_TOO_MANY_ATTEMPTS_이다() {
        store.issue(PHONE);

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertThat(store.verify(PHONE, "000000")).isEqualTo(VerifyResult.MISMATCH);
        }

        assertThat(store.verify(PHONE, "000000")).isEqualTo(VerifyResult.TOO_MANY_ATTEMPTS);
    }

    @Test
    void 최대_시도횟수를_넘긴_코드는_폐기되어_정답이어도_EXPIRED_이다() {
        String code = store.issue(PHONE);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            store.verify(PHONE, "000000");
        }

        VerifyResult result = store.verify(PHONE, code);

        assertThat(result).isEqualTo(VerifyResult.EXPIRED);
        assertThat(store.isVerified(PHONE)).isFalse();
    }

    @Test
    void 재발급하면_시도횟수가_초기화된다() {
        store.issue(PHONE);
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            store.verify(PHONE, "000000");
        }

        String code = store.issue(PHONE);

        assertThat(store.verify(PHONE, code)).isEqualTo(VerifyResult.OK);
    }

    @Test
    void 발급된_적_없는_번호_검증은_EXPIRED_이다() {
        VerifyResult result = store.verify(PHONE, "123456");

        assertThat(result).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    void 검증_성공한_번호를_재검증하면_ALREADY_VERIFIED_이다() {
        String code = store.issue(PHONE);
        store.verify(PHONE, code);

        VerifyResult result = store.verify(PHONE, code);

        assertThat(result).isEqualTo(VerifyResult.ALREADY_VERIFIED);
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

    @Test
    void 인메모리_현황은_발급_직후_코드를_만료_잔류로_세지_않는다() {
        store.issue(PHONE);

        assertThat(structureOf("codes").size()).isEqualTo(1);
        assertThat(structureOf("codes").breakdown()).containsEntry("만료됐는데 잔류", 0L);
    }

    @Test
    void 유효기간이_지난_코드가_남아있으면_인메모리_현황이_만료_잔류로_집계한다() {
        // 만료는 그 번호로 다시 접근할 때만 lazy 하게 판정되므로, TTL이 지나도 맵에는 그대로 남는다.
        VerificationCodeStore expiredStore = new VerificationCodeStore(new VerificationProperties(
                Duration.ofSeconds(-1), Duration.ofSeconds(60), Duration.ofMinutes(30),
                MAX_ATTEMPTS,
                Duration.ofHours(24), 5, Duration.ofHours(24), 1000));
        expiredStore.issue(PHONE);

        InMemoryStructureDto codes = expiredStore.inMemoryStructures().stream()
                .filter(structure -> structure.name().equals("codes"))
                .findFirst()
                .orElseThrow();

        assertThat(codes.size()).isEqualTo(1);
        assertThat(codes.breakdown()).containsEntry("만료됐는데 잔류", 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 유효기간이_지난_인증완료_상태도_인메모리_현황이_만료_잔류로_집계한다() {
        ConcurrentHashMap<String, LocalDateTime> verified =
                (ConcurrentHashMap<String, LocalDateTime>) ReflectionTestUtils.getField(store, "verified");
        verified.put(PHONE, LocalDateTime.now().minusMinutes(1));

        assertThat(structureOf("verified").size()).isEqualTo(1);
        assertThat(structureOf("verified").breakdown()).containsEntry("만료됐는데 잔류", 1L);
    }

    @Test
    void 인메모리_현황은_휴대폰번호와_인증번호를_노출하지_않는다() {
        store.issue(PHONE);

        assertThat(store.inMemoryStructures()).allSatisfy(structure ->
                assertThat(structure.samples()).isEmpty());
    }

    private InMemoryStructureDto structureOf(String name) {
        return store.inMemoryStructures().stream()
                .filter(structure -> structure.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("보고되지 않은 자료구조입니다: " + name));
    }
}
