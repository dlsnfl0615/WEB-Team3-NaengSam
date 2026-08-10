package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingDreamiInput 생성 시 필드 유효성 검증이 올바르게 동작하는지 확인한다.
 */
class MatchingDreamiInputTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void dreamiId가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingDreamiInput(null, LOCATION, Duration.ZERO));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void location이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingDreamiInput(UUID.randomUUID(), null, Duration.ZERO));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void waitingTime이_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new MatchingDreamiInput(UUID.randomUUID(), LOCATION, Duration.ofSeconds(-1)));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정상_필드로_생성하면_값이_그대로_저장된다() {
        UUID dreamiId = UUID.randomUUID();
        Duration waitingTime = Duration.ofMinutes(3);

        MatchingDreamiInput input = new MatchingDreamiInput(dreamiId, LOCATION, waitingTime);

        assertThat(input.dreamiId()).isEqualTo(dreamiId);
        assertThat(input.location()).isEqualTo(LOCATION);
        assertThat(input.waitingTime()).isEqualTo(waitingTime);
    }
}
