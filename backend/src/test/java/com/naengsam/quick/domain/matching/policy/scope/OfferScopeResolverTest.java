package com.naengsam.quick.domain.matching.policy.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties.OfferScopeThreshold;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * OfferScopeResolver가 주문 대기시간 임계값 경계에서 정확한 scope를 고르고, 잘못 설정된 임계값 목록(빈 목록·0부터
 * 시작하지 않음·중복 minOrderWait·음수 minOrderWait)을 생성 시점에 거부하는지 확인한다.
 */
class OfferScopeResolverTest {

    private static final List<OfferScopeThreshold> DEFAULT_THRESHOLDS = List.of(
            new OfferScopeThreshold(Duration.ZERO, 3_000),
            new OfferScopeThreshold(Duration.ofSeconds(60), 6_000)
    );

    private final OfferScopeResolver resolver = new OfferScopeResolver(DEFAULT_THRESHOLDS);

    @Test
    void 대기시간이_59초면_좁은_scope를_고른다() {
        OfferScope scope = resolver.resolve(Duration.ofSeconds(59));

        assertThat(scope.maxPickupDistanceMeters()).isEqualTo(3_000);
    }

    @Test
    void 대기시간이_60초_경계면_넓은_scope로_전환된다() {
        OfferScope scope = resolver.resolve(Duration.ofSeconds(60));

        assertThat(scope.maxPickupDistanceMeters()).isEqualTo(6_000);
    }

    @Test
    void 대기시간이_61초면_넓은_scope를_유지한다() {
        OfferScope scope = resolver.resolve(Duration.ofSeconds(61));

        assertThat(scope.maxPickupDistanceMeters()).isEqualTo(6_000);
    }

    @Test
    void 대기시간이_음수면_0으로_취급해_가장_좁은_scope를_고른다() {
        OfferScope scope = resolver.resolve(Duration.ofSeconds(-1));

        assertThat(scope.maxPickupDistanceMeters()).isEqualTo(3_000);
    }

    @Test
    void 임계값_목록이_비어있으면_예외() {
        Throwable thrown = catchThrowable(() -> new OfferScopeResolver(List.of()));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 가장_작은_minOrderWait가_0이_아니면_예외() {
        Throwable thrown = catchThrowable(() -> new OfferScopeResolver(
                List.of(new OfferScopeThreshold(Duration.ofSeconds(10), 3_000))));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minOrderWait가_중복되면_예외() {
        Throwable thrown = catchThrowable(() -> new OfferScopeResolver(List.of(
                new OfferScopeThreshold(Duration.ZERO, 3_000),
                new OfferScopeThreshold(Duration.ZERO, 6_000))));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minOrderWait가_음수면_예외() {
        Throwable thrown = catchThrowable(() -> new OfferScopeResolver(List.of(
                new OfferScopeThreshold(Duration.ofSeconds(-1), 3_000))));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxPickupDistanceMeters가_0_이하면_예외() {
        Throwable thrown = catchThrowable(() -> new OfferScopeResolver(List.of(
                new OfferScopeThreshold(Duration.ZERO, 0))));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
