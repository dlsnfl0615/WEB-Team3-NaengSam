package com.naengsam.quick.domain.matching.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OrderWaitScorePolicy가 주문 대기시간만으로 점수를 계산하는지 확인한다.
 */
class OrderWaitScorePolicyTest {

    private final OrderWaitScorePolicy policy = new OrderWaitScorePolicy();

    @Test
    void 오래_기다린_주문의_점수가_낮다() {
        MatchingCandidate longWaited = candidate(Duration.ofMinutes(30), Duration.ZERO, 0L);
        MatchingCandidate shortWaited = candidate(Duration.ofMinutes(5), Duration.ZERO, 0L);

        assertThat(policy.score(longWaited)).isLessThan(policy.score(shortWaited));
    }

    @Test
    void 드리미_대기시간과_거리는_결과에_영향을_주지_않는다() {
        MatchingCandidate base = candidate(Duration.ofMinutes(10), Duration.ZERO, 0L);
        MatchingCandidate differentDreamiWaitAndDistance = candidate(Duration.ofMinutes(10), Duration.ofHours(5), 999L);

        assertThat(policy.score(differentDreamiWaitAndDistance)).isEqualTo(policy.score(base));
    }

    @Test
    void 매우_긴_대기시간에서도_overflow가_발생하지_않는다() {
        MatchingCandidate candidate = candidate(Duration.ofDays(365 * 1000L), Duration.ZERO, 0L);

        assertThat(policy.score(candidate)).isEqualTo(-candidate.orderWaitingTime().toMillis());
    }

    private MatchingCandidate candidate(Duration orderWaitingTime, Duration dreamiWaitingTime, long distanceMeters) {
        return new MatchingCandidate(
                UUID.randomUUID(), UUID.randomUUID(), distanceMeters,
                orderWaitingTime, dreamiWaitingTime, 0, 0);
    }
}
