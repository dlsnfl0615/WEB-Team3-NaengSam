package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.service.MatchingService.MatchOffer;
import java.util.UUID;

/**
 * 드리미에게 새 제안 팝업을 띄울 때 전달하는 payload.
 */
public record OfferPopupPayload(UUID offerId, UUID orderId) {

    public static OfferPopupPayload from(MatchOffer offer) {
        return new OfferPopupPayload(offer.offerId(), offer.orderId());
    }
}
