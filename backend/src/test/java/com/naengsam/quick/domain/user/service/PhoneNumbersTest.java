package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 전화번호 정규화 유틸 단위 테스트. 발송/검증/가입/중복검사에서 동일 형식(숫자만)을 보장하는지 확인한다.
 */
class PhoneNumbersTest {

    @Test
    void null_입력은_null_을_반환한다() {
        assertThat(PhoneNumbers.normalize(null)).isNull();
    }

    @Test
    void 하이픈이_포함된_번호는_숫자만_남긴다() {
        assertThat(PhoneNumbers.normalize("010-1234-5678")).isEqualTo("01012345678");
    }

    @Test
    void 공백_괄호_플러스가_섞인_번호는_숫자만_남긴다() {
        assertThat(PhoneNumbers.normalize("+82 (10) 1234-5678")).isEqualTo("821012345678");
    }

    @Test
    void 이미_숫자만인_번호는_그대로_반환한다() {
        assertThat(PhoneNumbers.normalize("01012345678")).isEqualTo("01012345678");
    }
}
