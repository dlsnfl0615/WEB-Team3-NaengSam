package com.naengsam.quick.domain.matching.policy.config;

import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.global.notification.NotificationService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * {@link MatchingPolicyProperties}의 선택값에 따라 배정/점수/적격성 정책 Bean을 조립한다. 배정 정책은 점수 정책에 의존하므로, 점수 정책 Bean을 먼저 만들어 주입한다.
 */
@Configuration
@RequiredArgsConstructor
public class MatchingPolicyConfiguration {

    /**
     * 드리미 응답 제한시간. {@link com.naengsam.quick.domain.matching.service.MatchingService}과 동일한 값.
     */
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);

    private final MatchingPolicyProperties properties;

    @Bean
    public MatchingScorePolicy matchingScorePolicy() {
        return switch (properties.scoringPolicy()) {
            case ORDER_WAIT -> new OrderWaitScorePolicy();
            case BALANCED -> {
                MatchingPolicyProperties.BalancedWeights weights = properties.balancedWeights();
                yield new BalancedScorePolicy(
                        new BalancedScoreWeights(
                                weights.distanceWeight(), weights.orderWaitWeight(), weights.dreamiWaitWeight()),
                        weights.maxMatchingDistance(),
                        weights.targetOrderWait(),
                        weights.targetDreamiWait());
            }
        };
    }

    @Bean
    public MatchingAssignmentPolicy matchingAssignmentPolicy(MatchingScorePolicy matchingScorePolicy) {
        return switch (properties.assignmentPolicy()) {
            case LEGACY_ORDER_FIRST -> new LegacyOrderFirstAssignmentPolicy(matchingScorePolicy);
            case SCORE_BASED_GREEDY -> new ScoreBasedGreedyAssignmentPolicy(matchingScorePolicy);
        };
    }

    @Bean
    public MatchingEligibilityPolicy matchingEligibilityPolicy() {
        return switch (properties.eligibilityPolicy()) {
            case LEGACY -> new LegacyOfferPolicy();
            case OUTCOME_COOLDOWN -> {
                MatchingPolicyProperties.Cooldown cooldown = properties.cooldown();
                yield new OutcomeCooldownOfferPolicy(
                        cooldown.dreamiRejection(), cooldown.boormiRejection(), cooldown.dreamiExpiration());
            }
        };
    }

    @Bean
    public MatchingAssignmentProblemFactory matchingAssignmentProblemFactory(
            MatchingEligibilityPolicy matchingEligibilityPolicy) {
        return new MatchingAssignmentProblemFactory(matchingEligibilityPolicy);
    }

    @Bean
    public MatchingPlanValidator matchingPlanValidator(MatchingEligibilityPolicy matchingEligibilityPolicy) {
        return new MatchingPlanValidator(matchingEligibilityPolicy);
    }

    @Bean
    public MatchingPlanApplier matchingPlanApplier(
            MatchingPlanValidator matchingPlanValidator,
            @Lazy MatchingService matchingService,
            NotificationService notificationService) {
        return new MatchingPlanApplier(
                matchingPlanValidator, matchingService, notificationService, OFFER_TTL);
    }
}
