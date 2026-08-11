package com.naengsam.quick.domain.matching.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchOffer가 생성 시각과 상태 전이 시각(statusUpdatedAt)을 외부에서 전달받은 값으로 정확히 기록하는지,
 * 그리고 잘못된 상태 전이·null 시각을 거부하며 그 경우 상태/시각이 바뀌지 않는지 확인한다.
 */
class MatchOfferTest {

    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID DREAMI_ID = UUID.randomUUID();
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 9, 9, 0, 30);

    @Test
    void 생성_시각이_statusUpdatedAt에_저장된다() {
        MatchOffer offer = offeredOffer();

        assertThat(offer.statusUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 드리미_거절시_상태와_statusUpdatedAt이_갱신된다() {
        MatchOffer offer = offeredOffer();

        offer.rejectByDreami(OCCURRED_AT);

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.DREAMI_REJECTED);
        assertThat(offer.statusUpdatedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 회수시_상태와_statusUpdatedAt이_갱신된다() {
        MatchOffer offer = offeredOffer();

        offer.withdraw(OCCURRED_AT);

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);
        assertThat(offer.statusUpdatedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 부르미_거절시_상태와_statusUpdatedAt이_갱신된다() {
        MatchOffer offer = offeredOffer();
        offer.acceptByDreami(CREATED_AT);

        offer.rejectByBoormi(OCCURRED_AT);

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.BOORMI_REJECTED);
        assertThat(offer.statusUpdatedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 드리미_응답만료시_상태와_statusUpdatedAt이_갱신된다() {
        MatchOffer offer = offeredOffer();

        offer.expireByDreami(OCCURRED_AT);

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);
        assertThat(offer.statusUpdatedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 부르미_응답만료시_상태와_statusUpdatedAt이_갱신된다() {
        MatchOffer offer = offeredOffer();
        offer.acceptByDreami(CREATED_AT);

        offer.expireByBoormi(OCCURRED_AT);

        assertThat(offer.status()).isEqualTo(MatchOfferStatus.BOORMI_EXPIRED);
        assertThat(offer.statusUpdatedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 잘못된_상태_전이면_예외가_발생하고_상태와_시각이_유지된다() {
        MatchOffer offer = offeredOffer();
        offer.withdraw(CREATED_AT);

        Throwable thrown = catchThrowable(() -> offer.rejectByDreami(OCCURRED_AT));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);
        assertThat(offer.statusUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 상태_전이에_null_시각을_전달하면_예외가_발생하고_상태와_시각이_유지된다() {
        MatchOffer offer = offeredOffer();

        Throwable thrown = catchThrowable(() -> offer.rejectByDreami(null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(offer.statusUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 생성_시각이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchOffer(OFFER_ID, ORDER_ID, DREAMI_ID, MatchOfferStatus.OFFERED, null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchOffer offeredOffer() {
        return new MatchOffer(OFFER_ID, ORDER_ID, DREAMI_ID, MatchOfferStatus.OFFERED, CREATED_AT);
    }
}
