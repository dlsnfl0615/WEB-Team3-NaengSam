package com.naengsam.quick.domain.matching.event;

import java.util.UUID;

/**
 * 요청 처리 실패 사유를 대상 사용자에게 전달하는 payload. offerId는 이 오류의 원인이 된 제안이 있을 때만 채우고, 등록/주문
 * 단위 오류처럼 특정 제안과 무관하면 null로 둔다.
 */
public record NotificationErrorPayload(UUID offerId, String message) {
}
