package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingAssignmentProblemAssembler가 엔진 상태(OrderOfferGroup/WaitingDreami)에서 상태 필터링·거리 계산· 이전 오퍼 이력 조회를 거쳐
 * MatchingAssignmentProblemFactory에 넘길 입력을 올바르게 만드는지 확인한다.
 */
class MatchingAssignmentProblemAssemblerTest {

    private static final GeoPoint ORDER_LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    // 그리드 프리필터가 좌표를 직접 읽으므로 주문과 같은 셀에 들어가는 값이어야 한다. 거리 자체는 아래 테스트들이
    // GeoDistanceCalculator를 목킹해 정하므로, 여기서는 "같은 셀"이라는 점만 의미가 있다.
    private static final GeoPoint DREAMI_LOCATION =
            new GeoPoint(BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.001));
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 10, 12, 0);
    private static final Clock CLOCK =
            Clock.fixed(EVALUATED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final double DISTANCE_METERS = 500.0;
    private MatchingAssignmentProblemAssembler assembler;
    private List<OrderOfferGroup> orderOfferGroups;
    private List<WaitingDreami> waitingDreamis;

    private static OrderOfferGroup group(UUID orderId, OrderOfferGroupStatus status) {
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, List.of(), EVALUATED_AT.minusHours(1));
        switch (status) {
            case WAITING -> {
            }
            case OPEN -> group.addOffersAndOpen(List.of());
            case MATCHED -> group.confirmMatch();
            case CANCELLED -> group.cancel();
        }
        return group;
    }

    private static WaitingDreami dreami(UUID dreamiId, WaitingDreamiStatus status) {
        WaitingDreami dreami = new WaitingDreami(dreamiId, DREAMI_LOCATION, WaitingDreamiStatus.MATCHING,
                EVALUATED_AT.minusMinutes(5));
        if (status == WaitingDreamiStatus.PROPOSED) {
            dreami.markProposed();
        }
        return dreami;
    }

    private static MatchingPolicyProperties matchingPolicyProperties() {
        return matchingPolicyProperties(OfferQuotaMode.FIXED);
    }

    private static MatchingPolicyProperties matchingPolicyProperties(OfferQuotaMode offerQuotaMode) {
        return matchingPolicyProperties(offerQuotaMode, 5);
    }

    private static MatchingPolicyProperties matchingPolicyProperties(OfferQuotaMode offerQuotaMode, int dynamicQuotaMax) {
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                3,
                offerQuotaMode,
                dynamicQuotaMax,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10),
                        Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));
    }

    private static MatchingPolicyProperties matchingPolicyPropertiesWithOfferScopes(
            List<MatchingPolicyProperties.OfferScopeThreshold> offerScopes) {
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                3,
                OfferQuotaMode.FIXED,
                5,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10),
                        Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                offerScopes);
    }

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(DISTANCE_METERS);

        MatchingPolicyProperties properties = matchingPolicyProperties();
        MatchingAssignmentProblemFactory factory = new MatchingAssignmentProblemFactory(new LegacyOfferPolicy());
        meterRegistry = new SimpleMeterRegistry();
        assembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, factory, properties, CLOCK,
                new OfferScopeResolver(properties.offerScopes()), meterRegistry);
        orderOfferGroups = List.of();
        waitingDreamis = List.of();
    }

    private MatchingAssignmentProblem assemble() {
        return assembler.assemble(orderOfferGroups, waitingDreamis);
    }

    private MatchingAssignmentProblem assemble(MatchingPolicyProperties properties) {
        return assemble(properties, DISTANCE_METERS);
    }

    private MatchingAssignmentProblem assemble(MatchingPolicyProperties properties, double distanceMeters) {
        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(distanceMeters);
        MatchingAssignmentProblemFactory factory = new MatchingAssignmentProblemFactory(new LegacyOfferPolicy());
        meterRegistry = new SimpleMeterRegistry();
        MatchingAssignmentProblemAssembler dynamicAssembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, factory, properties, CLOCK,
                new OfferScopeResolver(properties.offerScopes()), meterRegistry);
        return dynamicAssembler.assemble(orderOfferGroups, waitingDreamis);
    }

    private static List<OrderOfferGroup> waitingGroups(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> group(UUID.randomUUID(), OrderOfferGroupStatus.WAITING))
                .toList();
    }

    private static List<WaitingDreami> matchingDreamis(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> dreami(UUID.randomUUID(), WaitingDreamiStatus.MATCHING))
                .toList();
    }

    @Test
    void WAITING_주문만_MatchingOrderInput으로_변환되고_나머지_상태는_제외된다() {
        UUID waitingOrderId = UUID.randomUUID();
        orderOfferGroups = List.of(
                group(waitingOrderId, OrderOfferGroupStatus.WAITING),
                group(UUID.randomUUID(), OrderOfferGroupStatus.OPEN),
                group(UUID.randomUUID(), OrderOfferGroupStatus.MATCHED),
                group(UUID.randomUUID(), OrderOfferGroupStatus.CANCELLED)
        );

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(waitingOrderId);
    }

    @Test
    void MATCHING_드리미만_MatchingDreamiInput으로_변환되고_PROPOSED는_제외된다() {
        UUID matchingDreamiId = UUID.randomUUID();
        waitingDreamis = List.of(
                dreami(matchingDreamiId, WaitingDreamiStatus.MATCHING),
                dreami(UUID.randomUUID(), WaitingDreamiStatus.PROPOSED)
        );

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.dreamis()).extracting(MatchingDreamiInput::dreamiId).containsExactly(matchingDreamiId);
    }

    @Test
    void 주문과_드리미_사이의_거리를_계산해서_후보에_담는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.candidates()).hasSize(1);
        assertThat(problem.candidates().get(0).distanceMeters()).isEqualTo(DISTANCE_METERS);
    }

    /**
     * offer scope 기반 최대 픽업거리 필터: 주문 대기시간으로 고른 scope의 maxPickupDistanceMeters를 넘는 후보는
     * candidate 자체가 만들어지지 않고, 그 사실이 pickup_distance_exceeded 지표로 남는다.
     */
    @Test
    void 거리가_scope의_최대_픽업거리와_같으면_후보에_포함된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));
        MatchingPolicyProperties properties = matchingPolicyPropertiesWithOfferScopes(
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));

        MatchingAssignmentProblem problem = assemble(properties, 3_000.0);

        assertThat(problem.candidates()).hasSize(1);
        assertThat(meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                .count()).isZero();
    }

    @Test
    void 거리가_scope의_최대_픽업거리를_넘으면_후보에서_제외되고_지표가_증가한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));
        MatchingPolicyProperties properties = matchingPolicyPropertiesWithOfferScopes(
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));

        MatchingAssignmentProblem problem = assemble(properties, 3_000.1);

        assertThat(problem.candidates()).isEmpty();
        assertThat(meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                .count()).isEqualTo(1.0);
    }

    @Test
    void 주문의_모든_드리미가_거리_초과면_그_주문의_candidate가_전부_비고_배정_정책도_빈_plan을_만든다() {
        UUID orderId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(
                dreami(UUID.randomUUID(), WaitingDreamiStatus.MATCHING),
                dreami(UUID.randomUUID(), WaitingDreamiStatus.MATCHING));
        MatchingPolicyProperties properties = matchingPolicyPropertiesWithOfferScopes(
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));

        MatchingAssignmentProblem problem = assemble(properties, 3_000.1);

        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(orderId);
        assertThat(problem.candidates()).isEmpty();
        assertThat(meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                .count()).isEqualTo(2.0);

        MatchingPlan plan = new LegacyOrderFirstAssignmentPolicy(
                new OrderWaitScorePolicy(), new OfferScopeResolver(properties.offerScopes())).createPlan(problem);

        assertThat(plan.proposals()).isEmpty();
    }

    @Test
    void 주문_대기시간이_길어져_더_넓은_scope가_적용되면_이전에_제외되던_거리의_후보도_포함된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        // 매칭 시작 후 61초가 지나, 두 번째 임계값(60초 이상 -> 6000m)이 적용되는 주문.
        orderOfferGroups = List.of(new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, List.of(), EVALUATED_AT.minusSeconds(61)));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));
        MatchingPolicyProperties properties = matchingPolicyPropertiesWithOfferScopes(List.of(
                new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000),
                new MatchingPolicyProperties.OfferScopeThreshold(Duration.ofSeconds(60), 6_000)));

        MatchingAssignmentProblem problem = assemble(properties, 5_000.0);

        assertThat(problem.candidates()).hasSize(1);
    }

    @Test
    void 같은_조합의_가장_최근_종료_이력을_previousInteraction으로_선택한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchOffer> offers = new ArrayList<>();
        offers.add(new MatchOffer(UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.DREAMI_REJECTED,
                EVALUATED_AT.minusMinutes(30)));
        offers.add(new MatchOffer(UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.BOORMI_EXPIRED,
                EVALUATED_AT.minusMinutes(10)));
        orderOfferGroups = List.of(new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, offers, EVALUATED_AT.minusHours(1)));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assemble();

        PreviousOfferInteraction interaction = problem.candidates().get(0).previousInteraction().orElseThrow();
        assertThat(interaction.outcome()).isEqualTo(PreviousOfferOutcome.BOORMI_EXPIRED);
        assertThat(interaction.occurredAt()).isEqualTo(EVALUATED_AT.minusMinutes(10));
    }

    // 드리미 응답 timeout 쿨다운 하의 다음 batch 재평가: 그룹은 WAITING, 드리미는 MATCHING이라 raw candidate 생성 대상은
    // 되지만(problem.orders()/problem.dreamis()에 그대로 나타남), 직전 상호작용이 DREAMI_EXPIRED인 조합은 쿨다운이 끝나기
    // 전에는 candidate에서 제외되고, 쿨다운이 끝난 뒤에는 같은 조합이 previousInteraction=DREAMI_EXPIRED를 유지한 채 다시 candidate로 생성된다.

    @Test
    void 드리미_응답_timeout_쿨다운_중에는_같은_주문_드리미_조합이_candidate에서_제외된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Duration dreamiExpirationCooldown = Duration.ofMinutes(5);
        LocalDateTime expiredAt = EVALUATED_AT.minusMinutes(2); // 쿨다운(5분) 안에 있음
        List<MatchOffer> offers = List.of(
                new MatchOffer(UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.DREAMI_EXPIRED, expiredAt));
        orderOfferGroups = List.of(new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, offers, EVALUATED_AT.minusHours(1)));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assembleWithOutcomeCooldown(dreamiExpirationCooldown);

        // 그룹 WAITING·드리미 MATCHING이라 raw candidate 생성 대상 자체에서는 빠지지 않는다.
        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(orderId);
        assertThat(problem.dreamis()).extracting(MatchingDreamiInput::dreamiId).containsExactly(dreamiId);
        // 하지만 쿨다운이 남아있어 최종 candidate 목록에서는 제외된다.
        assertThat(problem.candidates()).isEmpty();
    }

    @Test
    void 드리미_응답_timeout_쿨다운이_끝난_후에는_같은_조합이_DREAMI_EXPIRED_이력을_유지한_채_다시_candidate로_생성된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Duration dreamiExpirationCooldown = Duration.ofMinutes(5);
        LocalDateTime expiredAt = EVALUATED_AT.minusMinutes(6); // 쿨다운(5분)을 이미 지남
        List<MatchOffer> offers = List.of(
                new MatchOffer(UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.DREAMI_EXPIRED, expiredAt));
        orderOfferGroups = List.of(new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, offers, EVALUATED_AT.minusHours(1)));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assembleWithOutcomeCooldown(dreamiExpirationCooldown);

        assertThat(problem.candidates()).hasSize(1);
        PreviousOfferInteraction interaction = problem.candidates().get(0).previousInteraction().orElseThrow();
        assertThat(interaction.outcome()).isEqualTo(PreviousOfferOutcome.DREAMI_EXPIRED);
        assertThat(interaction.occurredAt()).isEqualTo(expiredAt);
    }

    private MatchingAssignmentProblem assembleWithOutcomeCooldown(Duration dreamiExpirationCooldown) {
        GeoDistanceCalculator geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(DISTANCE_METERS);
        MatchingPolicyProperties properties = new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                3,
                OfferQuotaMode.FIXED,
                5,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.OUTCOME_COOLDOWN,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(5), Duration.ofMinutes(10), dreamiExpirationCooldown),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));
        MatchingAssignmentProblemFactory factory = new MatchingAssignmentProblemFactory(
                new OutcomeCooldownOfferPolicy(
                        properties.cooldown().dreamiRejection(), properties.cooldown().boormiRejection(),
                        properties.cooldown().dreamiExpiration()));
        MatchingAssignmentProblemAssembler cooldownAssembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, factory, properties, CLOCK,
                new OfferScopeResolver(properties.offerScopes()), new SimpleMeterRegistry());
        return cooldownAssembler.assemble(orderOfferGroups, waitingDreamis);
    }

    @Test
    void 이전_오퍼_이력이_없으면_Optional_empty이다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.candidates().get(0).previousInteraction()).isEmpty();
    }

    @Test
    void 대기_시작_시각이_평가_시각보다_미래면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        orderOfferGroups = List.of(new OrderOfferGroup(
                orderId, UUID.randomUUID(), ORDER_LOCATION, null, List.of(), EVALUATED_AT.plusMinutes(1)));

        Throwable thrown = catchThrowable(this::assemble);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문이나_드리미가_없어도_허용된다() {
        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.orders()).isEmpty();
        assertThat(problem.dreamis()).isEmpty();
        assertThat(problem.candidates()).isEmpty();
    }

    @Test
    void 모든_주문에_동일한_고정_maxConcurrentOffers가_적용된다() {
        orderOfferGroups = List.of(
                group(UUID.randomUUID(), OrderOfferGroupStatus.WAITING),
                group(UUID.randomUUID(), OrderOfferGroupStatus.WAITING)
        );

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.orders())
                .extracting(MatchingOrderInput::maxConcurrentOffers)
                .containsOnly(matchingPolicyProperties().maxConcurrentOffers());
    }

    @Test
    void DYNAMIC_모드는_대기_드리미를_대기_주문_수로_나눠_올림한_값을_quota로_쓴다() {
        orderOfferGroups = waitingGroups(3);
        waitingDreamis = matchingDreamis(7);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.DYNAMIC));

        assertThat(problem.orders()).extracting(MatchingOrderInput::maxConcurrentOffers).containsOnly(3);
    }

    @Test
    void DYNAMIC_모드에서_드리미와_주문_수가_같으면_quota는_1이다() {
        orderOfferGroups = waitingGroups(3);
        waitingDreamis = matchingDreamis(3);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.DYNAMIC));

        assertThat(problem.orders()).extracting(MatchingOrderInput::maxConcurrentOffers).containsOnly(1);
    }

    @Test
    void DYNAMIC_모드에서_대기_드리미가_없어도_quota는_최소_1이다() {
        orderOfferGroups = waitingGroups(3);
        waitingDreamis = matchingDreamis(0);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.DYNAMIC));

        assertThat(problem.orders()).extracting(MatchingOrderInput::maxConcurrentOffers).containsOnly(1);
    }

    @Test
    void DYNAMIC_모드에서_비율이_기본_상한_5를_넘어도_quota는_최대_5이다() {
        orderOfferGroups = waitingGroups(1);
        waitingDreamis = matchingDreamis(20);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.DYNAMIC));

        assertThat(problem.orders()).extracting(MatchingOrderInput::maxConcurrentOffers).containsOnly(5);
    }

    @Test
    void DYNAMIC_모드의_quota_상한은_matching_dynamic_quota_max_설정값을_따른다() {
        orderOfferGroups = waitingGroups(1);
        waitingDreamis = matchingDreamis(20);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.DYNAMIC, 8));

        assertThat(problem.orders()).extracting(MatchingOrderInput::maxConcurrentOffers).containsOnly(8);
    }

    @Test
    void FIXED_모드는_대기_드리미_수와_무관하게_설정값을_그대로_쓴다() {
        orderOfferGroups = waitingGroups(1);
        waitingDreamis = matchingDreamis(20);

        MatchingAssignmentProblem problem = assemble(matchingPolicyProperties(OfferQuotaMode.FIXED));

        assertThat(problem.orders())
                .extracting(MatchingOrderInput::maxConcurrentOffers)
                .containsOnly(matchingPolicyProperties().maxConcurrentOffers());
    }

    @Test
    void 생성한_입력을_MatchingAssignmentProblemFactory에_전달해_결과를_만든다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        waitingDreamis = List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING));

        MatchingAssignmentProblem problem = assemble();

        assertThat(problem.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(orderId);
        assertThat(problem.dreamis()).extracting(MatchingDreamiInput::dreamiId).containsExactly(dreamiId);
        assertThat(problem.candidates()).extracting(candidate -> candidate.orderId()).containsExactly(orderId);
    }

    /**
     * 위 테스트들은 거리를 고정값으로 돌려주는 목 계산기를 쓰므로 그리드 프리필터가 실제로 무엇을 거르는지 확인할 수
     * 없다. 아래 두 개는 진짜 GeoDistanceCalculator를 넣고 좌표로만 판정한다.
     */
    private MatchingAssignmentProblem assembleWithRealDistances() {
        MatchingPolicyProperties properties = matchingPolicyProperties();
        MatchingAssignmentProblemFactory factory = new MatchingAssignmentProblemFactory(new LegacyOfferPolicy());
        meterRegistry = new SimpleMeterRegistry();
        MatchingAssignmentProblemAssembler realDistanceAssembler = new MatchingAssignmentProblemAssembler(
                new GeoDistanceCalculator(), factory, properties, CLOCK,
                new OfferScopeResolver(properties.offerScopes()), meterRegistry);
        return realDistanceAssembler.assemble(orderOfferGroups, waitingDreamis);
    }

    private static WaitingDreami dreamiAt(GeoPoint location) {
        return new WaitingDreami(UUID.randomUUID(), location, WaitingDreamiStatus.MATCHING,
                EVALUATED_AT.minusMinutes(5));
    }

    @Test
    void 그리드_이웃_밖의_드리미는_후보로_만들어지지_않는다() {
        orderOfferGroups = List.of(group(UUID.randomUUID(), OrderOfferGroupStatus.WAITING));
        // ORDER_LOCATION(0,0)에서 약 111km 떨어져 3000m scope의 이웃 셀에 들어오지 않는다.
        waitingDreamis = List.of(dreamiAt(new GeoPoint(BigDecimal.ONE, BigDecimal.ZERO)));

        MatchingAssignmentProblem problem = assembleWithRealDistances();

        assertThat(problem.candidates()).isEmpty();
        // 하버사인까지 가지 않고 걸러졌으므로 거리 초과 지표도 오르지 않는다.
        assertThat(meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                .count()).isZero();
    }

    @Test
    void 그리드_이웃_안이고_반경_안인_드리미는_후보로_만들어진다() {
        UUID orderId = UUID.randomUUID();
        orderOfferGroups = List.of(group(orderId, OrderOfferGroupStatus.WAITING));
        // ORDER_LOCATION(0,0)에서 약 157m — 3000m scope 안이다.
        waitingDreamis = List.of(dreamiAt(new GeoPoint(BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.001))));

        MatchingAssignmentProblem problem = assembleWithRealDistances();

        assertThat(problem.candidates()).extracting(MatchingCandidate::orderId).containsExactly(orderId);
        assertThat(problem.candidates().getFirst().distanceMeters()).isLessThan(3_000);
    }
}
