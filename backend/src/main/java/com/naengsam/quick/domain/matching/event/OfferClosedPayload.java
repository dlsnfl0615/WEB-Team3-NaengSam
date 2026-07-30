package com.naengsam.quick.domain.matching.event;

import java.util.UUID;

/**
 * 드리미의 제안 팝업을 닫아야 할 때 전달하는 payload. reason은 마감 사유(선착순 마감, 거절 완료 등).
 */
public record OfferClosedPayload(UUID offerId, String reason) {
}
