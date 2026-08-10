package com.naengsam.quick.domain.matching.model;

import java.time.Duration;
import java.util.UUID;

/**
 * 점수 계산에 필요한 매칭 후보의 원시 입력값. {@link com.naengsam.quick.domain.matching.service.scoring.MatchingScorePolicy}가
 * 소비하는 불변 값 객체이며, 필드 유효성은 생성 시점에 강제한다.
 */
public record MatchingCandidate(
        UUID orderId,
        UUID dreamiId,
        double distanceMeters,
        Duration orderWaitingTime,
        Duration dreamiWaitingTime,
        int orderCandidateCount,
        int dreamiCandidateCount
) {
    public MatchingCandidate {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId는 null일 수 없습니다.");
        }
        if (dreamiId == null) {
            throw new IllegalArgumentException("dreamiId는 null일 수 없습니다.");
        }
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters는 음수일 수 없습니다: " + distanceMeters);
        }
        if (orderWaitingTime == null || orderWaitingTime.isNegative()) {
            throw new IllegalArgumentException("orderWaitingTime은 null이거나 음수일 수 없습니다: " + orderWaitingTime);
        }
        if (dreamiWaitingTime == null || dreamiWaitingTime.isNegative()) {
            throw new IllegalArgumentException("dreamiWaitingTime은 null이거나 음수일 수 없습니다: " + dreamiWaitingTime);
        }
        if (orderCandidateCount < 0) {
            throw new IllegalArgumentException("orderCandidateCount는 음수일 수 없습니다: " + orderCandidateCount);
        }
        if (dreamiCandidateCount < 0) {
            throw new IllegalArgumentException("dreamiCandidateCount는 음수일 수 없습니다: " + dreamiCandidateCount);
        }
    }
}
