package com.naengsam.quick.domain.matching.policy.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingProposal;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.PlanningPolicyType;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import com.naengsam.quick.domain.matching.policy.eligibility.OutcomeCooldownOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Object graph와 primitive index planning policy가 기존 우선순위와 제약을 똑같이 적용하는지 검증한다. */
class PrimitiveIndexMatchingPlanningPolicyTest {

    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 17, 12, 0);
    private static final Clock CLOCK = Clock.fixed(EVALUATED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final GeoPoint ORDER_A_LOCATION = point(0, 0);
    private static final GeoPoint ORDER_B_LOCATION = point(1, 0);
    private static final GeoPoint DREAMI_1_LOCATION = point(10, 0);
    private static final GeoPoint DREAMI_2_LOCATION = point(11, 0);
    private static final GeoPoint DREAMI_3_LOCATION = point(12, 0);
    private static final GeoPoint DREAMI_4_LOCATION = point(13, 0);
    private static final UUID ORDER_A = uuid(1);
    private static final UUID ORDER_B = uuid(2);
    private static final UUID DREAMI_1 = uuid(11);
    private static final UUID DREAMI_2 = uuid(12);
    private static final UUID DREAMI_3 = uuid(13);
    private static final UUID DREAMI_4 = uuid(14);

    private static Stream<Arguments> policyCombinations() {
        List<Arguments> arguments = new ArrayList<>();
        for (AssignmentPolicyType assignment : AssignmentPolicyType.values()) {
            for (ScoringPolicyType scoring : ScoringPolicyType.values()) {
                for (EligibilityPolicyType eligibility : EligibilityPolicyType.values()) {
                    arguments.add(Arguments.of(assignment, scoring, eligibility));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("policyCombinations")
    void 두_planning_policy는_모든_정책_조합에서_같은_계획을_만든다(
            AssignmentPolicyType assignment,
            ScoringPolicyType scoring,
            EligibilityPolicyType eligibility
    ) {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.DYNAMIC, assignment, scoring, eligibility);
        GeoDistanceCalculator distanceCalculator = matrixDistanceCalculator();
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));
        Fixture fixture = fixtureWithPreviousInteractions();

        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(fixture.groups(), fixture.dreamis());
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(fixture.groups(), fixture.dreamis());

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals()).doesNotHaveDuplicates();
        assertThat(primitiveResult.plan().proposals())
                .extracting(MatchingProposal::dreamiId)
                .doesNotHaveDuplicates();
        assertThat(primitiveResult.plan().proposals().stream()
                .collect(java.util.stream.Collectors.groupingBy(MatchingProposal::orderId,
                        java.util.stream.Collectors.counting())))
                .allSatisfy((orderId, count) -> assertThat(count).isLessThanOrEqualTo(2));
    }

    @Test
    void scope_경계값은_포함하고_초과값은_제외한다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.BALANCED,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(ORDER_A_LOCATION, DREAMI_1_LOCATION)).thenReturn(3_000.0);
        when(distanceCalculator.distanceMeters(ORDER_A_LOCATION, DREAMI_2_LOCATION)).thenReturn(3_000.1);
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));
        OrderOfferGroup group = group(ORDER_A, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(2), List.of());
        List<WaitingDreami> dreamis = List.of(
                dreami(DREAMI_1, DREAMI_1_LOCATION, Duration.ofMinutes(2)),
                dreami(DREAMI_2, DREAMI_2_LOCATION, Duration.ofMinutes(2)));

        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(List.of(group), dreamis);
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(List.of(group), dreamis);

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals())
                .extracting(MatchingProposal::dreamiId)
                .containsExactly(DREAMI_1);
    }

    @Test
    void 점수와_대기시간이_같으면_UUID_순서를_재현한다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.BALANCED,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(any(), any())).thenReturn(100.0);
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));
        OrderOfferGroup group = group(ORDER_A, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(2), List.of());
        UUID lowerDreamiId = uuid(20);
        UUID higherDreamiId = uuid(21);
        List<WaitingDreami> dreamis = List.of(
                dreami(higherDreamiId, DREAMI_1_LOCATION, Duration.ofMinutes(2)),
                dreami(lowerDreamiId, DREAMI_2_LOCATION, Duration.ofMinutes(2)));

        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(List.of(group), dreamis);
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(List.of(group), dreamis);

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals().getFirst().dreamiId()).isEqualTo(lowerDreamiId);
    }

    @Test
    void 전역_정렬에서_점수와_양쪽_대기시간이_같으면_주문_UUID_순서를_재현한다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.BALANCED,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(any(), any())).thenReturn(100.0);
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));
        UUID lowerOrderId = uuid(30);
        UUID higherOrderId = uuid(31);
        List<OrderOfferGroup> groups = List.of(
                group(higherOrderId, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(2), List.of()),
                group(lowerOrderId, ORDER_B_LOCATION, EVALUATED_AT.minusMinutes(2), List.of()));
        WaitingDreami dreami = dreami(DREAMI_1, DREAMI_1_LOCATION, Duration.ofMinutes(2));

        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(groups, List.of(dreami));
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(groups, List.of(dreami));

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals().getFirst().orderId()).isEqualTo(lowerOrderId);
    }

    @Test
    void 큰_라운드_후_작은_라운드를_실행해도_이전_버퍼_데이터가_섞이지_않는다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(any(), any())).thenReturn(100.0);
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));

        List<OrderOfferGroup> largeGroups = new ArrayList<>();
        List<WaitingDreami> largeDreamis = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            largeGroups.add(group(uuid(100 + index), ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(1), List.of()));
            largeDreamis.add(dreami(uuid(200 + index), DREAMI_1_LOCATION, Duration.ofMinutes(1)));
        }
        policies.primitive().createPlan(largeGroups, largeDreamis);

        OrderOfferGroup smallGroup = group(ORDER_A, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(1), List.of());
        WaitingDreami smallDreami = dreami(DREAMI_1, DREAMI_1_LOCATION, Duration.ofMinutes(1));
        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(List.of(smallGroup), List.of(smallDreami));
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(
                List.of(smallGroup), List.of(smallDreami));

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals()).hasSize(1);
    }

    @Test
    void 빈_입력은_빈_계획을_반환한다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.DYNAMIC,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        PolicyPair policies = policies(properties, distanceCalculator, scoringPolicy(properties),
                eligibilityPolicy(properties));

        MatchingPlanningResult objectResult = policies.objectGraph().createPlan(List.of(), List.of());
        MatchingPlanningResult primitiveResult = policies.primitive().createPlan(List.of(), List.of());

        assertEquivalent(objectResult, primitiveResult);
        assertThat(primitiveResult.plan().proposals()).isEmpty();
    }

    @Test
    void 적격_후보의_점수는_후보당_한_번만_계산한다() {
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(any(), any())).thenReturn(100.0);
        AtomicInteger scoreCalls = new AtomicInteger();
        MatchingScorePolicy countingScorePolicy = candidate -> {
            scoreCalls.incrementAndGet();
            return 0;
        };
        PolicyPair policies = policies(properties, distanceCalculator, countingScorePolicy,
                eligibilityPolicy(properties));
        List<OrderOfferGroup> groups = List.of(
                group(ORDER_A, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(1), List.of()),
                group(ORDER_B, ORDER_B_LOCATION, EVALUATED_AT.minusMinutes(1), List.of()));
        List<WaitingDreami> dreamis = List.of(
                dreami(DREAMI_1, DREAMI_1_LOCATION, Duration.ofMinutes(1)),
                dreami(DREAMI_2, DREAMI_2_LOCATION, Duration.ofMinutes(1)));

        policies.primitive().createPlan(groups, dreamis);

        assertThat(scoreCalls).hasValue(4);
    }

    @Test
    void 후보_행렬이_int_한도를_넘으면_거리_계산_전에_실패한다() {
        MatchingPlanningSnapshotFactory snapshotFactory = mock(MatchingPlanningSnapshotFactory.class);
        MatchingPlanningSnapshot snapshot = mock(MatchingPlanningSnapshot.class);
        List<?> largeOrders = mock(List.class);
        List<?> largeDreamis = mock(List.class);
        when(largeOrders.size()).thenReturn(50_000);
        when(largeDreamis.size()).thenReturn(50_000);
        when(snapshot.orders()).thenReturn((List) largeOrders);
        when(snapshot.dreamis()).thenReturn((List) largeDreamis);
        when(snapshotFactory.create(any(), any())).thenReturn(snapshot);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        MatchingPolicyProperties properties = properties(
                OfferQuotaMode.FIXED,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY);
        PrimitiveIndexMatchingPlanningPolicy policy = new PrimitiveIndexMatchingPlanningPolicy(
                snapshotFactory,
                distanceCalculator,
                eligibilityPolicy(properties),
                scoringPolicy(properties),
                properties,
                new SimpleMeterRegistry());

        Throwable thrown = catchThrowable(() -> policy.createPlan(List.of(), List.of()));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("int 인덱스 한도");
        verify(distanceCalculator, never()).distanceMeters(any(), any());
    }

    private static PolicyPair policies(
            MatchingPolicyProperties properties,
            GeoDistanceCalculator distanceCalculator,
            MatchingScorePolicy scorePolicy,
            MatchingEligibilityPolicy eligibilityPolicy
    ) {
        OfferScopeResolver offerScopeResolver = new OfferScopeResolver(properties.offerScopes());
        MatchingPlanningSnapshotFactory snapshotFactory =
                new MatchingPlanningSnapshotFactory(properties, CLOCK, offerScopeResolver);
        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                distanceCalculator,
                new MatchingAssignmentProblemFactory(eligibilityPolicy),
                snapshotFactory,
                new SimpleMeterRegistry());
        MatchingAssignmentPolicy assignmentPolicy = switch (properties.assignmentPolicy()) {
            case LEGACY_ORDER_FIRST -> new LegacyOrderFirstAssignmentPolicy(scorePolicy, offerScopeResolver);
            case SCORE_BASED_GREEDY -> new ScoreBasedGreedyAssignmentPolicy(scorePolicy, offerScopeResolver);
        };
        return new PolicyPair(
                new ObjectGraphMatchingPlanningPolicy(assembler, assignmentPolicy),
                new PrimitiveIndexMatchingPlanningPolicy(
                        snapshotFactory,
                        distanceCalculator,
                        eligibilityPolicy,
                        scorePolicy,
                        properties,
                        new SimpleMeterRegistry()));
    }

    private static void assertEquivalent(
            MatchingPlanningResult objectResult,
            MatchingPlanningResult primitiveResult
    ) {
        assertThat(primitiveResult.plan()).isEqualTo(objectResult.plan());
        assertThat(primitiveResult.validationProblem().evaluatedAt())
                .isEqualTo(objectResult.validationProblem().evaluatedAt());
        assertThat(primitiveResult.validationProblem().orders())
                .isEqualTo(objectResult.validationProblem().orders());
        assertThat(primitiveResult.validationProblem().dreamis())
                .isEqualTo(objectResult.validationProblem().dreamis());

        Map<CandidateKey, MatchingCandidate> objectCandidates = new HashMap<>();
        for (MatchingCandidate candidate : objectResult.validationProblem().candidates()) {
            objectCandidates.put(new CandidateKey(candidate.orderId(), candidate.dreamiId()), candidate);
        }
        List<MatchingCandidate> expectedSelectedCandidates = objectResult.plan().proposals().stream()
                .map(proposal -> objectCandidates.get(new CandidateKey(proposal.orderId(), proposal.dreamiId())))
                .toList();
        assertThat(primitiveResult.validationProblem().candidates())
                .containsExactlyElementsOf(expectedSelectedCandidates);
    }

    private static Fixture fixtureWithPreviousInteractions() {
        MatchOffer recentDreamiRejection = new MatchOffer(
                uuid(301), ORDER_B, DREAMI_1, MatchOfferStatus.DREAMI_REJECTED, EVALUATED_AT.minusMinutes(1));
        MatchOffer withdrawn = new MatchOffer(
                uuid(302), ORDER_B, DREAMI_2, MatchOfferStatus.WITHDRAWN, EVALUATED_AT.minusMinutes(1));
        MatchOffer oldDreamiRejection = new MatchOffer(
                uuid(303), ORDER_A, DREAMI_3, MatchOfferStatus.DREAMI_REJECTED, EVALUATED_AT.minusMinutes(20));

        List<OrderOfferGroup> groups = List.of(
                group(ORDER_B, ORDER_B_LOCATION, EVALUATED_AT.minusMinutes(5),
                        List.of(recentDreamiRejection, withdrawn)),
                group(ORDER_A, ORDER_A_LOCATION, EVALUATED_AT.minusMinutes(10),
                        List.of(oldDreamiRejection)));
        List<WaitingDreami> dreamis = List.of(
                dreami(DREAMI_1, DREAMI_1_LOCATION, Duration.ofMinutes(5)),
                dreami(DREAMI_2, DREAMI_2_LOCATION, Duration.ofMinutes(10)),
                dreami(DREAMI_3, DREAMI_3_LOCATION, Duration.ofMinutes(7)),
                dreami(DREAMI_4, DREAMI_4_LOCATION, Duration.ofMinutes(3)));
        return new Fixture(groups, dreamis);
    }

    private static GeoDistanceCalculator matrixDistanceCalculator() {
        GeoDistanceCalculator calculator = mock(GeoDistanceCalculator.class);
        when(calculator.distanceMeters(ORDER_B_LOCATION, DREAMI_1_LOCATION)).thenReturn(100.0);
        when(calculator.distanceMeters(ORDER_B_LOCATION, DREAMI_2_LOCATION)).thenReturn(100.0);
        when(calculator.distanceMeters(ORDER_B_LOCATION, DREAMI_3_LOCATION)).thenReturn(3_001.0);
        when(calculator.distanceMeters(ORDER_B_LOCATION, DREAMI_4_LOCATION)).thenReturn(400.0);
        when(calculator.distanceMeters(ORDER_A_LOCATION, DREAMI_1_LOCATION)).thenReturn(100.0);
        when(calculator.distanceMeters(ORDER_A_LOCATION, DREAMI_2_LOCATION)).thenReturn(100.0);
        when(calculator.distanceMeters(ORDER_A_LOCATION, DREAMI_3_LOCATION)).thenReturn(3_000.0);
        when(calculator.distanceMeters(ORDER_A_LOCATION, DREAMI_4_LOCATION)).thenReturn(400.0);
        return calculator;
    }

    private static MatchingPolicyProperties properties(
            OfferQuotaMode quotaMode,
            AssignmentPolicyType assignment,
            ScoringPolicyType scoring,
            EligibilityPolicyType eligibility
    ) {
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                2,
                quotaMode,
                2,
                PlanningPolicyType.PRIMITIVE_INDEX,
                assignment,
                scoring,
                eligibility,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 6_000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                List.of(
                        new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 1_000),
                        new MatchingPolicyProperties.OfferScopeThreshold(Duration.ofMinutes(1), 3_000)));
    }

    private static MatchingScorePolicy scoringPolicy(MatchingPolicyProperties properties) {
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

    private static MatchingEligibilityPolicy eligibilityPolicy(MatchingPolicyProperties properties) {
        return switch (properties.eligibilityPolicy()) {
            case LEGACY -> new LegacyOfferPolicy();
            case OUTCOME_COOLDOWN -> {
                MatchingPolicyProperties.Cooldown cooldown = properties.cooldown();
                yield new OutcomeCooldownOfferPolicy(
                        cooldown.dreamiRejection(), cooldown.boormiRejection(), cooldown.dreamiExpiration());
            }
        };
    }

    private static OrderOfferGroup group(
            UUID orderId,
            GeoPoint location,
            LocalDateTime matchingStartedAt,
            List<MatchOffer> offers
    ) {
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, uuid(999), location, null, offers, matchingStartedAt);
        if (!offers.isEmpty()) {
            group.addOffersAndOpen(List.of());
            group.closeForRematch();
        }
        return group;
    }

    private static WaitingDreami dreami(UUID dreamiId, GeoPoint location, Duration waitingTime) {
        return new WaitingDreami(
                dreamiId, location, WaitingDreamiStatus.MATCHING, EVALUATED_AT.minus(waitingTime));
    }

    private static GeoPoint point(long latitude, long longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }

    private record PolicyPair(
            ObjectGraphMatchingPlanningPolicy objectGraph,
            PrimitiveIndexMatchingPlanningPolicy primitive
    ) {
    }

    private record Fixture(List<OrderOfferGroup> groups, List<WaitingDreami> dreamis) {
    }

    private record CandidateKey(UUID orderId, UUID dreamiId) {
    }
}
