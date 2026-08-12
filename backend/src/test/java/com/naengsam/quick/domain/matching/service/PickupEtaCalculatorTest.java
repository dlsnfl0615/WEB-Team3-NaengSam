package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 직선거리를 도보 속도(약 4km/h) 기준 분 단위로 올림 변환하는지 확인한다.
 */
class PickupEtaCalculatorTest {

    @Test
    void 거리를_도보속도로_환산해_분_단위로_올림한다() {
        // 66.67m/min 가정 → 900m는 13.5분, ceil로 14분.
        assertThat(PickupEtaCalculator.minutesFromDistance(900.0)).isEqualTo(14);
    }

    @Test
    void 나누어_떨어지는_거리도_올림_처리된다() {
        // 정확히 1분(66.67m)보다 살짝 못 미치는 경우도 올림되어 1분이다.
        assertThat(PickupEtaCalculator.minutesFromDistance(1.0)).isEqualTo(1);
    }

    @Test
    void 거리가_0이면_0분이다() {
        assertThat(PickupEtaCalculator.minutesFromDistance(0.0)).isEqualTo(0);
    }
}
