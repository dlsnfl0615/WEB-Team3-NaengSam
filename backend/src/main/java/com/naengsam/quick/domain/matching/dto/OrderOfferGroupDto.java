package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.service.MatchingService;
import java.util.List;
import java.util.UUID;

public record OrderOfferGroupDto(
        UUID orderId,
        MatchingService.OrderOfferGroupStatus status,
        boolean rematchRequired,
        List<MatchOfferDto> offers
) {

    public static OrderOfferGroupDto from(MatchingService.OrderOfferGroup group) {
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
            MatchingService.MatchOfferStatus status
    ) {

        public static MatchOfferDto from(MatchingService.MatchOffer offer) {
            return new MatchOfferDto(offer.offerId(), offer.orderId(), offer.dreamiId(), offer.status());
        }
    }
}
