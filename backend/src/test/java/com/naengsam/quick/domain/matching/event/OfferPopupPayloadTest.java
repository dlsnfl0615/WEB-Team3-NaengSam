package com.naengsam.quick.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.dto.OfferPolicyDto;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OfferPopupPayload가 offeredAt(오퍼 생성 시각)과 expiresAt(offeredAt + ttl)을 절대 시각으로 담는지 확인한다.
 */
class OfferPopupPayloadTest {

    private static final LocalDateTime OFFERED_AT = LocalDateTime.of(2026, 8, 11, 9, 0, 0);
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void offeredAt과_expiresAt이_오퍼_생성시각_기준으로_계산된다() {
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT);
        OrderSummaryDto summary = orderSummary();

        OfferPopupPayload payload = OfferPopupPayload.from(offer, summary, TTL);

        assertThat(payload.offeredAt()).isEqualTo(OFFERED_AT);
        assertThat(payload.expiresAt()).isEqualTo(OFFERED_AT.plusSeconds(30));
    }

    @Test
    void offerPolicy는_오퍼에_저장된_스냅샷을_그대로_담는다() {
        OfferPolicySnapshot snapshot =
                new OfferPolicySnapshot(Duration.ZERO, OFFERED_AT, 0L, 500.0, 3_000);
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT,
                snapshot);

        OfferPopupPayload payload = OfferPopupPayload.from(offer, orderSummary(), TTL);

        assertThat(payload.offerPolicy()).isEqualTo(OfferPolicyDto.from(snapshot));
    }

    @Test
    void 오퍼에_스냅샷이_없으면_offerPolicy는_null이다() {
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT);

        OfferPopupPayload payload = OfferPopupPayload.from(offer, orderSummary(), TTL);

        assertThat(payload.offerPolicy()).isNull();
    }

    private OrderSummaryDto orderSummary() {
        return new OrderSummaryDto(
                UUID.randomUUID(), "품목", null, null, 5000L, 20, 1200L,
                BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), "픽업별칭", "픽업주소",
                BigDecimal.valueOf(37.2), BigDecimal.valueOf(127.2), "도착별칭", "도착주소",
                "img", "문 앞에 놓아주세요", OFFERED_AT);
    }
}
