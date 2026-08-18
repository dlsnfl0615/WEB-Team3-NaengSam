package com.naengsam.quick.global.sse;

/**
 * {@link SseConnectionManager#close}로 계정의 단일 SSE 연결을 종료할 때의 사유.
 */
public enum SseCloseReason {
    LOGOUT,
    SESSION_EXPIRED,
    REPLACED_BY_LOGIN,
    REPLACED,
    CLIENT_DISCONNECT,
    ACCOUNT_DISABLED,
    SHUTDOWN
}
