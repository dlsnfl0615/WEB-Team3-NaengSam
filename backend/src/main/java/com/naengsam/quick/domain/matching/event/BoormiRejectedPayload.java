package com.naengsam.quick.domain.matching.event;

import java.util.UUID;

/**
 * 부르미가 제안을 거절했을 때 해당 드리미에게 전달하는 payload.
 */
public record BoormiRejectedPayload(UUID offerId, UUID orderId) {
}
