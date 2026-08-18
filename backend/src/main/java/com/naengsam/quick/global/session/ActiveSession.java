package com.naengsam.quick.global.session;

import com.naengsam.quick.global.sse.SseConnection;

/**
 * {@link ActiveSessionRegistry}가 사용자당 하나만 유지하는 활성 세션. sessionId는 재로그인 시 이전 값과의
 * 비교/치환에만 쓰이므로 로그에 남기지 않는다.
 * <p>{@code sseConnection}은 이 세션이 보유한 서버 SSE emitter 슬롯(0..1)이다. 새로 로그인하면 {@link #create}로
 * 빈 슬롯의 세션을 만들고, 이후 SSE 구독이 성공하면 {@link #withSseConnection}으로 채운다.
 */
public record ActiveSession(
        String sessionId,
        LoginSession session,
        SseConnection sseConnection
) {

    public static ActiveSession create(LoginSession session) {
        return new ActiveSession(session.getSessionId(), session, null);
    }

    public ActiveSession withSseConnection(SseConnection sseConnection) {
        return new ActiveSession(sessionId, session, sseConnection);
    }

    public ActiveSession withoutSseConnection() {
        return new ActiveSession(sessionId, session, null);
    }
}
