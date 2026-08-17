package com.naengsam.quick.domain.matching.policy.scope;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 오퍼 후보가 선정된 순간 적용됐던 offer scope 판단 근거를 그대로 남긴 스냅샷.
 * {@link com.naengsam.quick.domain.matching.policy.assignment.MatchingProposal}에 실려, 그 뒤 설정(offer-scopes)이
 * 바뀌거나 주문 대기시간이 더 지나도 "그때 어떤 scope를 기준으로 이 후보를 통과시켰는지"를 다시 계산하지 않고 그대로
 * 확인할 수 있게 한다.
 *
 * @param scopeKey                 적용된 {@link OfferScope}를 고른 임계값의 minOrderWait
 * @param evaluatedAt              이 판단이 이뤄진 배치 평가 시각
 * @param orderWaitingSeconds      판단 시점의 주문 대기시간(초)
 * @param pickupDistanceMeters     실제 부르미-드리미 픽업거리
 * @param maxPickupDistanceMeters  그 시점에 적용된 scope가 허용한 최대 픽업거리
 */
public record OfferPolicySnapshot(
        Duration scopeKey,
        LocalDateTime evaluatedAt,
        long orderWaitingSeconds,
        double pickupDistanceMeters,
        long maxPickupDistanceMeters
) {
    public OfferPolicySnapshot {
        if (scopeKey == null || scopeKey.isNegative()) {
            throw new IllegalArgumentException("scopeKey는 null이거나 음수일 수 없습니다: " + scopeKey);
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt은 null일 수 없습니다.");
        }
        if (orderWaitingSeconds < 0) {
            throw new IllegalArgumentException("orderWaitingSeconds는 음수일 수 없습니다: " + orderWaitingSeconds);
        }
        if (pickupDistanceMeters < 0) {
            throw new IllegalArgumentException("pickupDistanceMeters는 음수일 수 없습니다: " + pickupDistanceMeters);
        }
        if (maxPickupDistanceMeters <= 0) {
            throw new IllegalArgumentException(
                    "maxPickupDistanceMeters는 0보다 커야 합니다: " + maxPickupDistanceMeters);
        }
        if (pickupDistanceMeters > maxPickupDistanceMeters) {
            throw new IllegalArgumentException(
                    "pickupDistanceMeters가 maxPickupDistanceMeters를 넘을 수 없습니다: "
                            + pickupDistanceMeters + " > " + maxPickupDistanceMeters);
        }
    }

    /**
     * 배정 정책이 후보를 제안으로 확정하는 순간, 그 후보에 적용 중인 offer scope를 그대로 스냅샷으로 남긴다.
     */
    public static OfferPolicySnapshot capture(
            OfferScopeResolver offerScopeResolver, MatchingCandidate candidate, LocalDateTime evaluatedAt) {
        Duration orderWaitingTime = candidate.orderWaitingTime();
        OfferScope offerScope = offerScopeResolver.resolve(orderWaitingTime);
        Duration scopeKey = offerScopeResolver.resolveScopeKey(orderWaitingTime);
        return new OfferPolicySnapshot(
                scopeKey, evaluatedAt, orderWaitingTime.toSeconds(), candidate.distanceMeters(),
                offerScope.maxPickupDistanceMeters());
    }
}
