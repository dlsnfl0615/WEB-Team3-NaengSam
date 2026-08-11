package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingOrderInput 생성 시 필드 유효성 검증, 특히 maxConcurrentOffers 제약과 {@link MatchingOrderInput#from}의
 * orderWaitingTime 계산이 올바르게 동작하는지 확인한다.
 */
class MatchingOrderInputTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime MATCHING_STARTED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    @Test
    void maxConcurrentOffers가_0이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingOrderInput(UUID.randomUUID(), LOCATION, Duration.ZERO, 0));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxConcurrentOffers가_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingOrderInput(UUID.randomUUID(), LOCATION, Duration.ZERO, -1));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxConcurrentOffers가_1이상이면_정상_생성된다() {
        MatchingOrderInput input =
                new MatchingOrderInput(UUID.randomUUID(), LOCATION, Duration.ZERO, 1);

        assertThat(input.maxConcurrentOffers()).isEqualTo(1);
    }

    @Test
    void orderId가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingOrderInput(null, LOCATION, Duration.ZERO, 1));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void location이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingOrderInput(UUID.randomUUID(), null, Duration.ZERO, 1));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void waitingTime이_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingOrderInput(UUID.randomUUID(), LOCATION, Duration.ofSeconds(-1), 1));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void from은_매칭_시작_시각부터_평가_시각까지를_orderWaitingTime으로_계산한다() {
        OrderOfferGroup group = groupStartedAt(MATCHING_STARTED_AT);
        LocalDateTime evaluatedAt = MATCHING_STARTED_AT.plusMinutes(5);

        MatchingOrderInput input = MatchingOrderInput.from(group, evaluatedAt, 1);

        assertThat(input.waitingTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(input.orderId()).isEqualTo(group.orderId());
        assertThat(input.location()).isEqualTo(group.location());
    }

    @Test
    void from은_평가_시각보다_매칭_시작_시각이_미래이면_예외가_발생한다() {
        OrderOfferGroup group = groupStartedAt(MATCHING_STARTED_AT);
        LocalDateTime evaluatedAt = MATCHING_STARTED_AT.minusSeconds(1);

        Throwable thrown = catchThrowable(() -> MatchingOrderInput.from(group, evaluatedAt, 1));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private OrderOfferGroup groupStartedAt(LocalDateTime matchingStartedAt) {
        return new OrderOfferGroup(UUID.randomUUID(), UUID.randomUUID(), LOCATION, null, List.of(), matchingStartedAt);
    }
}
