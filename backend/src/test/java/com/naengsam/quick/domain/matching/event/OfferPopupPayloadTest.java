package com.naengsam.quick.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OfferPopupPayload가 offeredAt(오퍼 생성 시각)과 expiresAt(offeredAt + ttl)을 zone 정보가 있는 절대 시각(Instant)으로
 * 담는지 확인한다. 프론트가 서버와 다른 시간대라도 시각을 잘못 해석하지 않도록 하기 위함이다.
 */
class OfferPopupPayloadTest {

    private static final LocalDateTime OFFERED_AT = LocalDateTime.of(2026, 8, 11, 9, 0, 0);
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void offeredAt과_expiresAt이_오퍼_생성시각_기준으로_계산된다() {
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, OFFERED_AT);
        OrderSummaryDto summary = orderSummary();
        var expectedOfferedAt = OFFERED_AT.atZone(ZoneId.systemDefault()).toInstant();

        OfferPopupPayload payload = OfferPopupPayload.from(offer, summary, TTL);

        assertThat(payload.offeredAt()).isEqualTo(expectedOfferedAt);
        assertThat(payload.expiresAt()).isEqualTo(expectedOfferedAt.plusSeconds(30));
    }

    private OrderSummaryDto orderSummary() {
        return new OrderSummaryDto(
                UUID.randomUUID(), "품목", null, null, 5000L, 20, 1200L,
                BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), "픽업별칭", "픽업주소",
                BigDecimal.valueOf(37.2), BigDecimal.valueOf(127.2), "도착별칭", "도착주소",
                "img", OFFERED_AT);
    }
}
