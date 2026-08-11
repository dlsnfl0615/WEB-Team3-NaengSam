package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import java.util.UUID;

/**
 * 드리미가 제안을 수락했을 때 부르미에게 드리미 정보를 전달하는 payload. pickupEtaMinutes는 실시간 경로가 아닌 직선거리 기반 추정치이므로,
 * 드리미/픽업지 위치를 알 수 없으면 null이다.
 */
public record DreamiInfoPayload(UUID offerId, UUID orderId, UUID dreamiId, Integer pickupEtaMinutes) {

    public static DreamiInfoPayload from(MatchOffer offer, Integer pickupEtaMinutes) {
        return new DreamiInfoPayload(offer.offerId(), offer.orderId(), offer.dreamiId(), pickupEtaMinutes);
    }
}
