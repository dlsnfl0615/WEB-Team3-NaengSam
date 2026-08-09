package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

/**
 * ScarcityScoreWeights 생성 시 필드 유효성 검증이 올바르게 동작하는지 확인한다.
 */
class ScarcityScoreWeightsTest {

    @Test
    void 가중치가_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new ScarcityScoreWeights(-10, 60, 50));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가중치_합이_0이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new ScarcityScoreWeights(0, 0, 0));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가중치_합이_음수이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new ScarcityScoreWeights(-10, -20, 5));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가중치_합이_100이_아니어도_생성된다() {
        ScarcityScoreWeights weights = new ScarcityScoreWeights(10, 45, 45);

        assertThat(weights.totalWeight()).isEqualTo(100);
    }
}
