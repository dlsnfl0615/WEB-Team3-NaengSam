package com.naengsam.quick.domain.user.service;

/**
 * 전화번호 정규화 유틸. 발송/검증/가입/중복검사에서 동일한 형식(숫자만)을 사용해 일관성을 보장한다.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String phone) {
        return phone == null ? null : phone.replaceAll("[^0-9]", "");
    }
}
