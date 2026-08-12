package com.naengsam.quick.domain.matching.policy.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매칭 정책 튜닝값. {@code matching.*} (application.properties) 로 바인딩된다.
 *
 * <ul>
 *   <li>batchWindow/maxConcurrentOffers : 배치 주기, 주문 하나가 동시에 가질 수 있는 오퍼 수(offerQuotaMode=FIXED일 때 사용)</li>
 *   <li>offerQuotaMode : maxConcurrentOffers를 고정값으로 쓸지(FIXED), 배치마다 드리미/주문 비율로 다시 계산할지(DYNAMIC)</li>
 *   <li>assignmentPolicy/scoringPolicy/eligibilityPolicy : 배정/점수/적격성 정책 선택</li>
 *   <li>cooldown : eligibilityPolicy=OUTCOME_COOLDOWN 일 때만 사용되는 결과별 쿨다운</li>
 *   <li>balancedWeights : scoringPolicy=BALANCED 일 때만 사용되는 가중치·기준값</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "matching")
public record MatchingPolicyProperties(
        Duration batchWindow,
        int maxConcurrentOffers,
        OfferQuotaMode offerQuotaMode,
        AssignmentPolicyType assignmentPolicy,
        ScoringPolicyType scoringPolicy,
        EligibilityPolicyType eligibilityPolicy,
        Cooldown cooldown,
        BalancedWeights balancedWeights
) {

    public record Cooldown(
            Duration dreamiRejection,
            Duration boormiRejection,
            Duration dreamiExpiration
    ) {
    }

    public record BalancedWeights(
            int distanceWeight,
            int orderWaitWeight,
            int dreamiWaitWeight,
            long maxMatchingDistance,
            Duration targetOrderWait,
            Duration targetDreamiWait
    ) {
    }
}
