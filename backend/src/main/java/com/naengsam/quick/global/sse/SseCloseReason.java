package com.naengsam.quick.global.sse;

/**
 * {@link SseEmitterRegistry#disconnectAll}로 사용자의 SSE 연결을 전체 종료할 때의 사유.
 */
public enum SseCloseReason {
    LOGOUT,
    SESSION_EXPIRED,
    REPLACED_BY_LOGIN,
    ACCOUNT_DISABLED,
    SHUTDOWN
}
