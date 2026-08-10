package com.naengsam.quick.domain.user.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호 해싱 유틸. 가입 시 계정마다 새 salt 를 붙여 단방향 해싱하고, 로그인은 같은 salt 로 재계산해 비교한다.
 * 알고리즘은 JDK 내장 PBKDF2WithHmacSHA256 을 쓴다(외부 의존성 없음).
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 210_000;
    private static final String SEPARATOR = ":";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PasswordHasher() {
    }

    /**
     * 평문 비밀번호를 해싱해 {@code "<saltHex>:<hashHex>"} 형식으로 반환한다(97자, 컬럼 varchar(255) 안에 들어간다).
     */
    public static String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        return HEX.formatHex(salt) + SEPARATOR + HEX.formatHex(derive(rawPassword, salt));
    }

    /**
     * 저장된 문자열에서 salt 를 꺼내 다시 해싱한 뒤 비교한다. 형식이 깨졌거나 평문이 그대로 저장된 값이면 false 를 반환한다.
     */
    public static boolean matches(String rawPassword, String stored) {
        if (stored == null) {
            return false;
        }

        String[] parts = stored.split(SEPARATOR);
        if (parts.length != 2) {
            return false;
        }

        byte[] salt;
        byte[] expected;
        try {
            salt = HEX.parseHex(parts[0]);
            expected = HEX.parseHex(parts[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return MessageDigest.isEqual(derive(rawPassword, salt), expected);
    }

    private static byte[] derive(String rawPassword, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("비밀번호 해싱에 실패했습니다.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
