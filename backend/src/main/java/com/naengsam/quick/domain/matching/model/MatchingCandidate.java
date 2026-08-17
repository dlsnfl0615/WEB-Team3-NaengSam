package com.naengsam.quick.domain.matching.model;

import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 점수 계산에 필요한 매칭 후보의 원시 입력값. {@link MatchingScorePolicy}가 소비하는 불변 값 객체이며, 필드 유효성은 생성 시점에 강제한다.
 * previousInteraction은 같은 주문-드리미 조합의 가장 최근 오퍼 이력 하나만 담으며, 이력이 없으면 {@code Optional.empty()}다.
 * 이 커밋에서는 이 필드를 실제 정책 판단에 사용하지 않는다.
 */
public record MatchingCandidate(
        UUID orderId,
        UUID dreamiId,
        double distanceMeters,
        Duration orderWaitingTime,
        Duration dreamiWaitingTime,
        int orderCandidateCount,
        int dreamiCandidateCount,
        Optional<PreviousOfferInteraction> previousInteraction
) implements MatchingCandidateView {
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
        // Optional의 내용물이 아니라 참조 자체가 null인지 검사한다 — Java는 Optional 타입 변수에도 null 대입을
        // 막지 않으므로, 호출자가 Optional.empty() 대신 null을 넘기는 실수를 생성 시점에 막기 위함이다.
        if (previousInteraction == null) {
            throw new IllegalArgumentException("previousInteraction은 null일 수 없습니다.");
        }
    }
}
