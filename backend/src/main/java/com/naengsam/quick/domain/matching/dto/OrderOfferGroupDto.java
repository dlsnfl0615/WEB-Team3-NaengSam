package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import java.util.List;
import java.util.UUID;

public record OrderOfferGroupDto(
        UUID orderId,
        OrderOfferGroupStatus status,
        boolean rematchRequired,
        List<MatchOfferDto> offers
) {

    public static OrderOfferGroupDto from(OrderOfferGroup group) {
        return new OrderOfferGroupDto(
                group.orderId(),
                group.status(),
                group.rematchRequired(),
                group.offers().stream().map(MatchOfferDto::from).toList()
        );
    }

    public record MatchOfferDto(
            UUID offerId,
            UUID orderId,
            UUID dreamiId,
            MatchOfferStatus status
    ) {

        public static MatchOfferDto from(MatchOffer offer) {
            return new MatchOfferDto(offer.offerId(), offer.orderId(), offer.dreamiId(), offer.status());
        }
    }
}
