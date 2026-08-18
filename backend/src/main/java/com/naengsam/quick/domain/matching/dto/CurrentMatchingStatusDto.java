package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 로그인 사용자 기준 현재 매칭 상태. 드리미로서 응답 대기 중인 제안(pendingOffer)과 부르미로서 확인 대기 중인 드리미 수락 정보(incomingDreami)를 함께
 * 담는다. 진행 중인 것이 없으면 해당 필드는 null이다.
 *
 * <p>dreamiOnline은 드리미가 매칭엔진에 등록돼 오퍼를 기다리는 중인지다. 클라이언트의 온라인/오프라인 버튼 상태는 메모리에만 있어 새로고침하면 사라지는데, 서버 등록은 그대로
 * 살아 있어 화면과 실제가 어긋난다. 이 필드가 그 복원 근거다.
 */
public record CurrentMatchingStatusDto(
        PendingOfferDto pendingOffer,
        DreamiInfoPayload incomingDreami,
        boolean dreamiOnline
) {

    public record PendingOfferDto(
            UUID offerId,
            OrderSummaryDto orderSummary,
            LocalDateTime offeredAt,
            LocalDateTime expiresAt,
            OfferPolicyDto offerPolicy
    ) {

        // SSE 유실 후 클라이언트가 폴백으로 이 API를 호출했을 때도, 팝업으로 받았을 expiresAt·offerPolicy와 같은 값을 복원해
        // 남은 시간과 탐색 범위를 다시 계산·재해석하지 않고 그대로 쓸 수 있게 한다.
        public static PendingOfferDto from(MatchOffer offer, OrderOfferGroup group, Duration ttl) {
            LocalDateTime offeredAt = offer.statusUpdatedAt();
            return new PendingOfferDto(
                    offer.offerId(), group.orderSummary(), offeredAt, offeredAt.plus(ttl),
                    OfferPolicyDto.from(offer.offerPolicySnapshot()));
        }
    }
}
