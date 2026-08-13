package com.naengsam.quick.domain.matching.policy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.OfferTimeoutScheduler;
import com.naengsam.quick.global.notification.NotificationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * matching.* 설정에 따라 배정/점수/적격성 정책 Bean이 올바르게 조립되는지, cooldown·가중치가
 * 바인딩되는지, 잘못된 값은 컨텍스트 기동 실패로 거부되는지 확인한다.
 */
class MatchingPolicyConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, MatchingPolicyConfiguration.class);

    @Test
    void 기본_설정이면_LEGACY_ORDER_FIRST_ORDER_WAIT_LEGACY_빈이_생성된다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=LEGACY"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MatchingPolicyProperties.class);
                    assertThat(context.getBean("matchingAssignmentPolicy"))
                            .isInstanceOf(LegacyOrderFirstAssignmentPolicy.class);
                    assertThat(context.getBean("matchingScorePolicy")).isInstanceOf(OrderWaitScorePolicy.class);
                    assertThat(context.getBean("matchingEligibilityPolicy")).isInstanceOf(LegacyOfferPolicy.class);
                });
    }

    @Test
    void 부하테스트_설정이면_SCORE_BASED_GREEDY_BALANCED_OUTCOME_COOLDOWN_빈으로_교체된다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=SCORE_BASED_GREEDY",
                        "matching.scoring-policy=BALANCED",
                        "matching.eligibility-policy=OUTCOME_COOLDOWN",
                        "matching.balanced-weights.distance-weight=1",
                        "matching.balanced-weights.order-wait-weight=1",
                        "matching.balanced-weights.dreami-wait-weight=1",
                        "matching.balanced-weights.max-matching-distance=1000",
                        "matching.balanced-weights.target-order-wait=5m",
                        "matching.balanced-weights.target-dreami-wait=5m",
                        "matching.cooldown.dreami-rejection=5m",
                        "matching.cooldown.boormi-rejection=10m",
                        "matching.cooldown.dreami-expiration=3m"
                )
                .run(context -> {
                    assertThat(context.getBean("matchingAssignmentPolicy"))
                            .isInstanceOf(ScoreBasedGreedyAssignmentPolicy.class);
                    assertThat(context.getBean("matchingScorePolicy")).isInstanceOf(BalancedScorePolicy.class);
                    assertThat(context.getBean("matchingEligibilityPolicy"))
                            .isInstanceOf(OutcomeCooldownOfferPolicy.class);
                });
    }

    @Test
    void cooldown과_가중치_설정값이_바인딩된다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=LEGACY",
                        "matching.cooldown.dreami-rejection=7m",
                        "matching.cooldown.boormi-rejection=11m",
                        "matching.cooldown.dreami-expiration=4m",
                        "matching.balanced-weights.distance-weight=2",
                        "matching.balanced-weights.order-wait-weight=3",
                        "matching.balanced-weights.dreami-wait-weight=4",
                        "matching.balanced-weights.max-matching-distance=500",
                        "matching.balanced-weights.target-order-wait=6m",
                        "matching.balanced-weights.target-dreami-wait=8m"
                )
                .run(context -> {
                    MatchingPolicyProperties properties = context.getBean(MatchingPolicyProperties.class);

                    assertThat(properties.cooldown().dreamiRejection()).isEqualTo(Duration.ofMinutes(7));
                    assertThat(properties.cooldown().boormiRejection()).isEqualTo(Duration.ofMinutes(11));
                    assertThat(properties.cooldown().dreamiExpiration()).isEqualTo(Duration.ofMinutes(4));
                    assertThat(properties.balancedWeights().distanceWeight()).isEqualTo(2);
                    assertThat(properties.balancedWeights().orderWaitWeight()).isEqualTo(3);
                    assertThat(properties.balancedWeights().dreamiWaitWeight()).isEqualTo(4);
                    assertThat(properties.balancedWeights().maxMatchingDistance()).isEqualTo(500);
                    assertThat(properties.balancedWeights().targetOrderWait()).isEqualTo(Duration.ofMinutes(6));
                    assertThat(properties.balancedWeights().targetDreamiWait()).isEqualTo(Duration.ofMinutes(8));
                });
    }

    @Test
    void offer_quota_mode가_FIXED_DYNAMIC으로_바인딩된다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=DYNAMIC",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=LEGACY"
                )
                .run(context -> {
                    MatchingPolicyProperties properties = context.getBean(MatchingPolicyProperties.class);
                    assertThat(properties.offerQuotaMode()).isEqualTo(OfferQuotaMode.DYNAMIC);
                });

        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=LEGACY"
                )
                .run(context -> {
                    MatchingPolicyProperties properties = context.getBean(MatchingPolicyProperties.class);
                    assertThat(properties.offerQuotaMode()).isEqualTo(OfferQuotaMode.FIXED);
                });
    }

    @Test
    void 잘못된_정책_이름이면_컨텍스트_기동이_실패한다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=UNKNOWN_POLICY",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=LEGACY"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void BALANCED_가중치가_모두_0이면_빈_생성이_실패한다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=BALANCED",
                        "matching.eligibility-policy=LEGACY",
                        "matching.balanced-weights.distance-weight=0",
                        "matching.balanced-weights.order-wait-weight=0",
                        "matching.balanced-weights.dreami-wait-weight=0",
                        "matching.balanced-weights.max-matching-distance=1000",
                        "matching.balanced-weights.target-order-wait=5m",
                        "matching.balanced-weights.target-dreami-wait=5m"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 음수_cooldown이면_빈_생성이_실패한다() {
        contextRunner
                .withPropertyValues(
                        "matching.batch-window=200ms",
                        "matching.max-concurrent-offers=3",
                        "matching.offer-quota-mode=FIXED",
                        "matching.assignment-policy=LEGACY_ORDER_FIRST",
                        "matching.scoring-policy=ORDER_WAIT",
                        "matching.eligibility-policy=OUTCOME_COOLDOWN",
                        "matching.cooldown.dreami-rejection=-5m",
                        "matching.cooldown.boormi-rejection=10m",
                        "matching.cooldown.dreami-expiration=3m"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(MatchingPolicyProperties.class)
    static class PropertiesConfig {

        @Bean
        OfferTimeoutScheduler offerTimeoutScheduler() {
            return mock(OfferTimeoutScheduler.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }
    }
}
