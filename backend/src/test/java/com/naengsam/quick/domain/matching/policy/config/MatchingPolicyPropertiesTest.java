package com.naengsam.quick.domain.matching.policy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * matching.dynamic-quota-max에 대한 바인딩 시점 검증을 확인한다.
 */
class MatchingPolicyPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "matching.batch-interval=1s",
                    "matching.max-concurrent-offers=3",
                    "matching.offer-quota-mode=DYNAMIC",
                    "matching.assignment-policy=SCORE_BASED_GREEDY",
                    "matching.scoring-policy=BALANCED",
                    "matching.eligibility-policy=OUTCOME_COOLDOWN",
                    "matching.cooldown.dreami-rejection=10m",
                    "matching.cooldown.boormi-rejection=10m",
                    "matching.cooldown.dreami-expiration=10m",
                    "matching.balanced-weights.distance-weight=1",
                    "matching.balanced-weights.order-wait-weight=1",
                    "matching.balanced-weights.dreami-wait-weight=1",
                    "matching.balanced-weights.max-matching-distance=3000",
                    "matching.balanced-weights.target-order-wait=5m",
                    "matching.balanced-weights.target-dreami-wait=5m"
            );

    @Test
    void dynamicQuotaMax가_0이면_애플리케이션_컨텍스트_생성이_실패한다() {
        contextRunner.withPropertyValues("matching.dynamic-quota-max=0")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void dynamicQuotaMax가_1이상이면_정상적으로_바인딩된다() {
        contextRunner.withPropertyValues("matching.dynamic-quota-max=5")
                .run(context -> assertThat(context.getBean(MatchingPolicyProperties.class).dynamicQuotaMax())
                        .isEqualTo(5));
    }

    @EnableConfigurationProperties(MatchingPolicyProperties.class)
    @Configuration
    static class TestConfig {
    }
}
