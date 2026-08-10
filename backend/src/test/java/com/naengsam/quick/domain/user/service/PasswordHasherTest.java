package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 비밀번호 해싱 유틸 단위 테스트. 평문 노출 방지, salt 랜덤성, 검증 성공/실패 분기를 확인한다.
 */
class PasswordHasherTest {

    @Test
    void 해싱_결과는_평문과_다르다() {
        String hashed = PasswordHasher.hash("pass123");

        assertThat(hashed).isNotEqualTo("pass123");
        assertThat(hashed).doesNotContain("pass123");
    }

    @Test
    void 해싱_같은_비밀번호도_매번_다른_salt로_다른_결과() {
        String first = PasswordHasher.hash("pass123");
        String second = PasswordHasher.hash("pass123");

        assertThat(first).isNotEqualTo(second);
        assertThat(PasswordHasher.matches("pass123", first)).isTrue();
        assertThat(PasswordHasher.matches("pass123", second)).isTrue();
    }

    @Test
    void 검증_올바른_비밀번호면_true() {
        String hashed = PasswordHasher.hash("pass123");

        assertThat(PasswordHasher.matches("pass123", hashed)).isTrue();
    }

    @Test
    void 검증_틀린_비밀번호면_false() {
        String hashed = PasswordHasher.hash("pass123");

        assertThat(PasswordHasher.matches("wrong", hashed)).isFalse();
    }

    @Test
    void 검증_저장형식이_깨졌으면_false() {
        assertThat(PasswordHasher.matches("pass123", "pass123")).isFalse();
        assertThat(PasswordHasher.matches("pass123", "zzzz:zzzz")).isFalse();
        assertThat(PasswordHasher.matches("pass123", "")).isFalse();
        assertThat(PasswordHasher.matches("pass123", null)).isFalse();
    }
}
