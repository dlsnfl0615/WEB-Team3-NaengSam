package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import java.util.UUID;

/**
 * 드리미가 제안을 수락했을 때 부르미에게 드리미 정보를 전달하는 payload.
 */
public record DreamiInfoPayload(UUID offerId, UUID orderId, UUID dreamiId) {

    public static DreamiInfoPayload from(MatchOffer offer) {
        return new DreamiInfoPayload(offer.offerId(), offer.orderId(), offer.dreamiId());
    }
}
