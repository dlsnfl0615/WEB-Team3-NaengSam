package com.naengsam.quick.domain.matching.policy.scope;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties.OfferScopeThreshold;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 주문 대기시간에 따라 적용할 {@link OfferScope}를 고른다. {@code matching.offer-scopes[]}로 설정된 임계값 중,
 * 대기시간 이하인 것 중 가장 큰(가장 최근에 조건을 만족한) minOrderWait의 scope를 적용한다.
 * <p>어떤 scope를 적용해야 하는지 고르는 역할만 담당하며, 실제로 후보를 거르는 건
 * {@link com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler}가 한다.
 */
public class OfferScopeResolver {

    private final NavigableMap<Duration, OfferScope> scopesByMinWait;

    public OfferScopeResolver(List<OfferScopeThreshold> thresholds) {
        this.scopesByMinWait = toValidatedMap(thresholds);
    }

    /**
     * 주문 대기시간에 해당하는 scope를 찾는다. 음수 대기시간은 0으로 취급한다.
     */
    public OfferScope resolve(Duration orderWaitingTime) {
        return floorEntry(orderWaitingTime).getValue();
    }

    /**
     * 주문 대기시간에 해당하는 scope를 고를 때 실제로 선택된 임계값의 key(minOrderWait)를 반환한다.
     * {@link com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot}가 "어떤 scope가 적용됐는지"를
     * 남길 때 쓴다.
     */
    public Duration resolveScopeKey(Duration orderWaitingTime) {
        return floorEntry(orderWaitingTime).getKey();
    }

    private Map.Entry<Duration, OfferScope> floorEntry(Duration orderWaitingTime) {
        Duration waitingTime = orderWaitingTime.isNegative() ? Duration.ZERO : orderWaitingTime;
        return scopesByMinWait.floorEntry(waitingTime);
    }

    private static NavigableMap<Duration, OfferScope> toValidatedMap(List<OfferScopeThreshold> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            throw new IllegalArgumentException("matching.offer-scopes는 비어 있을 수 없습니다.");
        }

        NavigableMap<Duration, OfferScope> result = new TreeMap<>();
        for (OfferScopeThreshold threshold : thresholds) {
            Duration minOrderWait = threshold.minOrderWait();
            if (minOrderWait == null || minOrderWait.isNegative()) {
                throw new IllegalArgumentException(
                        "offer-scope의 minOrderWait는 null이거나 음수일 수 없습니다: " + minOrderWait);
            }

            OfferScope scope = new OfferScope(threshold.maxPickupDistanceMeters());
            OfferScope duplicate = result.put(minOrderWait, scope);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "offer-scope의 minOrderWait는 중복될 수 없습니다: " + minOrderWait);
            }
        }

        Map.Entry<Duration, OfferScope> first = result.firstEntry();
        if (!first.getKey().isZero()) {
            throw new IllegalArgumentException(
                    "offer-scope는 대기시간 0부터 시작하는 임계값을 포함해야 합니다. 가장 작은 minOrderWait: "
                            + first.getKey());
        }

        return result;
    }
}
