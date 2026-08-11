package com.naengsam.quick.global.session;

/**
 * {@link ActiveSessionRegistry}가 사용자당 하나만 유지하는 활성 세션. sessionId는 재로그인 시 이전 값과의
 * 비교/치환에만 쓰이므로 로그에 남기지 않는다.
 */
public record ActiveSession(
        String sessionId,
        LoginSession session
) {
}
