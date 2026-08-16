package com.naengsam.quick.domain.matching.policy.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 매칭 정책 튜닝값. {@code matching.*} (application.properties) 로 바인딩된다.
 *
 * <ul>
 *   <li>batchInterval/maxConcurrentOffers : {@link com.naengsam.quick.domain.matching.service.PeriodicMatchingBatchScheduler}가 반복 배치를
 *   예약하는 주기, 주문 하나가 동시에 가질 수 있는 오퍼 수(offerQuotaMode=FIXED일 때 사용)</li>
 *   <li>offerQuotaMode : maxConcurrentOffers를 고정값으로 쓸지(FIXED), 배치마다 드리미/주문 비율로 다시 계산할지(DYNAMIC)</li>
 *   <li>dynamicQuotaMax : offerQuotaMode=DYNAMIC일 때 배치마다 계산되는 오퍼 수의 상한(하한은 항상 1)</li>
 *   <li>assignmentPolicy/scoringPolicy/eligibilityPolicy : 배정/점수/적격성 정책 선택</li>
 *   <li>cooldown : eligibilityPolicy=OUTCOME_COOLDOWN 일 때만 사용되는 결과별 쿨다운</li>
 *   <li>balancedWeights : scoringPolicy=BALANCED 일 때만 사용되는 가중치·기준값</li>
 *   <li>offerScopes : 주문 대기시간 구간별로 적용할 드리미 탐색 반경 임계값 목록. {@link com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver}가 소비한다.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "matching")
public record MatchingPolicyProperties(
        Duration batchInterval,
        int maxConcurrentOffers,
        OfferQuotaMode offerQuotaMode,
        @Min(1) int dynamicQuotaMax,
        AssignmentPolicyType assignmentPolicy,
        ScoringPolicyType scoringPolicy,
        EligibilityPolicyType eligibilityPolicy,
        Cooldown cooldown,
        BalancedWeights balancedWeights,
        List<OfferScopeThreshold> offerScopes
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

    /**
     * 주문 대기시간이 minOrderWait 이상이 되면 maxPickupDistanceMeters 반경을 적용한다는 임계값 하나.
     */
    public record OfferScopeThreshold(
            Duration minOrderWait,
            long maxPickupDistanceMeters
    ) {
    }
}
