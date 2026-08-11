package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 드리미가 제안을 수락했을 때 부르미에게 드리미 정보를 전달하는 payload. pickupEtaMinutes는 실시간 경로가 아닌 직선거리 기반 추정치이므로,
 * 드리미/픽업지 위치를 알 수 없으면 null이다. 부르미의 확인 응답 마감은 남은 시간(ttl)이 아니라 절대 시각(expiresAt)으로 내려준다 — SSE 전송이 지연되거나
 * 클라이언트가 이벤트를 나중에 처리해도 마감 시각이 흔들리지 않는다.
 */
public record DreamiInfoPayload(UUID offerId, UUID orderId, UUID dreamiId, Integer pickupEtaMinutes,
        LocalDateTime acceptedAt, LocalDateTime expiresAt) {

    public static DreamiInfoPayload from(MatchOffer offer, Integer pickupEtaMinutes, Duration ttl) {
        LocalDateTime acceptedAt = offer.statusUpdatedAt();
        return new DreamiInfoPayload(offer.offerId(), offer.orderId(), offer.dreamiId(), pickupEtaMinutes,
                acceptedAt, acceptedAt.plus(ttl));
    }
}
