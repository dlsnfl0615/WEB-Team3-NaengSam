package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 로그인 사용자 기준 현재 매칭 상태. 드리미로서 응답 대기 중인 제안(pendingOffer)과 부르미로서 확인 대기 중인 드리미 수락 정보(incomingDreami)를 함께
 * 담는다. 진행 중인 것이 없으면 해당 필드는 null이다.
 */
public record CurrentMatchingStatusDto(
        PendingOfferDto pendingOffer,
        DreamiInfoPayload incomingDreami
) {

    public record PendingOfferDto(
            UUID offerId,
            OrderSummaryDto orderSummary,
            Instant offeredAt,
            Instant expiresAt
    ) {

        // SSE 유실 후 클라이언트가 폴백으로 이 API를 호출했을 때도, 팝업으로 받았을 expiresAt과 같은 값을 복원해 남은 시간을 다시 계산할 수 있게 한다.
        public static PendingOfferDto from(MatchOffer offer, OrderOfferGroup group, Duration ttl) {
            Instant offeredAt = offer.statusUpdatedAt().atZone(ZoneId.systemDefault()).toInstant();
            return new PendingOfferDto(offer.offerId(), group.orderSummary(), offeredAt, offeredAt.plus(ttl));
        }
    }
}
