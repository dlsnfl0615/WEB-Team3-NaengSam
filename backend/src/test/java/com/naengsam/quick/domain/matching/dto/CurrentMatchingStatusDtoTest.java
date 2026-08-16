package com.naengsam.quick.domain.matching.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.naengsam.quick.domain.matching.dto.CurrentMatchingStatusDto.PendingOfferDto;
import com.naengsam.quick.domain.matching.event.OfferPopupPayload;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PendingOfferDto가 SSE 유실 후 복구 API로 호출됐을 때도 offeredAt·expiresAt·offerPolicy를 팝업 발송 시와
 * 같은 값으로 복원하는지 확인한다.
 */
class CurrentMatchingStatusDtoTest {

    private static final LocalDateTime OFFERED_AT = LocalDateTime.of(2026, 8, 11, 9, 0, 0);
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void offerPolicy는_오퍼에_저장된_스냅샷을_그대로_담는다() {
        OfferPolicySnapshot snapshot = new OfferPolicySnapshot(Duration.ZERO, OFFERED_AT, 0L, 500.0, 3_000);
        UUID orderId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT, snapshot);
        OrderOfferGroup group = group(orderId, offer);

        PendingOfferDto dto = PendingOfferDto.from(offer, group, TTL);

        assertThat(dto.offerPolicy()).isEqualTo(OfferPolicyDto.from(snapshot));
    }

    @Test
    void 오퍼에_스냅샷이_없으면_offerPolicy는_null이다() {
        UUID orderId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT);
        OrderOfferGroup group = group(orderId, offer);

        PendingOfferDto dto = PendingOfferDto.from(offer, group, TTL);

        assertThat(dto.offerPolicy()).isNull();
    }

    @Test
    void SSE_팝업과_복구_API가_같은_offerPolicy를_반환한다() {
        OfferPolicySnapshot snapshot = new OfferPolicySnapshot(Duration.ofSeconds(60), OFFERED_AT, 61L, 4_000.0, 6_000);
        UUID orderId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT, snapshot);
        OrderOfferGroup group = group(orderId, offer);
        OrderSummaryDto summary = mock(OrderSummaryDto.class);

        OfferPopupPayload ssePayload = OfferPopupPayload.from(offer, summary, TTL);
        PendingOfferDto recoveryDto = PendingOfferDto.from(offer, group, TTL);

        assertThat(ssePayload.offerPolicy()).isEqualTo(recoveryDto.offerPolicy());
    }

    private OrderOfferGroup group(UUID orderId, MatchOffer offer) {
        return new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), mock(OrderSummaryDto.class),
                List.of(offer), OFFERED_AT.minusMinutes(1));
    }
}
