package com.naengsam.quick.domain.matching.event;

/**
 * 요청 처리 실패 사유를 대상 사용자에게 전달하는 payload.
 */
public record NotificationErrorPayload(String message) {
}
