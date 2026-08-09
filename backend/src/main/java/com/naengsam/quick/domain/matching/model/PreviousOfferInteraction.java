package com.naengsam.quick.domain.matching.model;

import java.time.LocalDateTime;

/**
 * 같은 주문-드리미 조합의 가장 최근 오퍼 이력 하나. {@link MatchingCandidate#previousInteraction()}이 이 값을
 * 담으며, 과거 이력이 없으면 {@code Optional.empty()}로 표현한다.
 */
public record PreviousOfferInteraction(
        PreviousOfferOutcome outcome,
        LocalDateTime occurredAt
) {
    public PreviousOfferInteraction {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome은 null일 수 없습니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 null일 수 없습니다.");
        }
    }
}
