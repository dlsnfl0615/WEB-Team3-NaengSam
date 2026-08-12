package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 드리미가 제안을 수락했을 때 부르미에게 드리미 정보를 전달하는 payload. pickupEtaMinutes는 실시간 경로가 아닌 직선거리 기반 추정치이므로,
 * 드리미/픽업지 위치를 알 수 없으면 null이다. 부르미의 확인 응답 마감은 남은 시간(ttl)이 아니라 절대 시각(expiresAt)으로 내려준다 — SSE 전송이 지연되거나
 * 클라이언트가 이벤트를 나중에 처리해도 마감 시각이 흔들리지 않는다. acceptedAt/expiresAt은 zone 정보가 없는 LocalDateTime이 아니라
 * Instant로 내려준다 — 그렇지 않으면 프론트가 서버와 다른 시간대에서 문자열을 로컬 시간으로 해석해 expiresAt이 이미 지난 것처럼 보일 수 있다.
 */
public record DreamiInfoPayload(UUID offerId, UUID orderId, UUID dreamiId, Integer pickupEtaMinutes,
        Instant acceptedAt, Instant expiresAt) {

    public static DreamiInfoPayload from(MatchOffer offer, Integer pickupEtaMinutes, Duration ttl) {
        Instant acceptedAt = offer.statusUpdatedAt().atZone(ZoneId.systemDefault()).toInstant();
        return new DreamiInfoPayload(offer.offerId(), offer.orderId(), offer.dreamiId(), pickupEtaMinutes,
                acceptedAt, acceptedAt.plus(ttl));
    }
}
