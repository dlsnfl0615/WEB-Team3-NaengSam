package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingOrderInput 생성 시 필드 유효성 검증, 특히 maxConcurrentOffers 제약이 올바르게 동작하는지 확인한다.
 */
class MatchingOrderInputTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

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
}
