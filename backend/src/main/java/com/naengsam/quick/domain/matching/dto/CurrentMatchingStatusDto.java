package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.util.UUID;

/**
 * 로그인 사용자 기준 현재 매칭 상태. 드리미로서 응답 대기 중인 제안(pendingOffer)과 부르미로서 확인 대기 중인 드리미 수락 정보(incomingDreami)를 함께
 * 담는다. 진행 중인 것이 없으면 해당 필드는 null이다.
 */
public record CurrentMatchingStatusDto(
        PendingOfferDto pendingOffer,
        DreamiInfoPayload incomingDreami
) {

    public record PendingOfferDto(UUID offerId, OrderSummaryDto orderSummary) {

        public static PendingOfferDto from(MatchOffer offer, OrderOfferGroup group) {
            return new PendingOfferDto(offer.offerId(), group.orderSummary());
        }
    }
}
