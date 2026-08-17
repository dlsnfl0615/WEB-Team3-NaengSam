package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.PlanningPolicyType;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.planning.MatchingPlanningPolicy;
import com.naengsam.quick.domain.matching.policy.planning.MatchingPlanningSnapshotFactory;
import com.naengsam.quick.domain.matching.policy.planning.ObjectGraphMatchingPlanningPolicy;
import com.naengsam.quick.domain.matching.policy.planning.PrimitiveIndexMatchingPlanningPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.BoormiOfferExpirationService;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.order.service.PendingOfferStateService;
import com.naengsam.quick.global.notification.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 두 planning policy를 MatchingService에 연결했을 때 오퍼와 상태 전이가 같은지 검증한다. */
class MatchingServicePlanningPolicyIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final UUID ORDER_A = new UUID(0, 1);
    private static final UUID ORDER_B = new UUID(0, 2);
    private static final UUID DREAMI_A = new UUID(0, 11);
    private static final UUID DREAMI_B = new UUID(0, 12);
    private static final UUID DREAMI_C = new UUID(0, 13);

    @Test
    void 두_planning_policy는_같은_오퍼와_드리미_상태를_만든다() {
        ServiceState objectGraphState = runCycle(PlanningPolicyType.OBJECT_GRAPH);
        ServiceState primitiveIndexState = runCycle(PlanningPolicyType.PRIMITIVE_INDEX);

        assertThat(primitiveIndexState).isEqualTo(objectGraphState);
    }

    private ServiceState runCycle(PlanningPolicyType planningPolicyType) {
        MatchingPolicyProperties properties = properties(planningPolicyType);
        GeoDistanceCalculator distanceCalculator = mock(GeoDistanceCalculator.class);
        when(distanceCalculator.distanceMeters(any(), any())).thenReturn(100.0);
        LegacyOfferPolicy eligibilityPolicy = new LegacyOfferPolicy();
        OfferScopeResolver scopeResolver = new OfferScopeResolver(properties.offerScopes());
        BalancedScorePolicy scorePolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(1, 1, 1),
                3_000,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
        MatchingPlanningSnapshotFactory snapshotFactory =
                new MatchingPlanningSnapshotFactory(properties, CLOCK, scopeResolver);
        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                distanceCalculator,
                new MatchingAssignmentProblemFactory(eligibilityPolicy),
                snapshotFactory,
                new SimpleMeterRegistry());
        MatchingPlanningPolicy planningPolicy = switch (planningPolicyType) {
            case OBJECT_GRAPH -> new ObjectGraphMatchingPlanningPolicy(
                    assembler, new ScoreBasedGreedyAssignmentPolicy(scorePolicy, scopeResolver));
            case PRIMITIVE_INDEX -> new PrimitiveIndexMatchingPlanningPolicy(
                    snapshotFactory,
                    distanceCalculator,
                    eligibilityPolicy,
                    scorePolicy,
                    properties,
                    new SimpleMeterRegistry());
        };

        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.isReachableNow(any())).thenReturn(true);
        OrderService orderService = mock(OrderService.class);
        Orders matchingOrder = mock(Orders.class);
        when(matchingOrder.getOrderCd()).thenReturn(OrderCd.MATCHING);
        when(orderService.findOrders(any())).thenAnswer(invocation -> {
            List<UUID> orderIds = invocation.getArgument(0);
            return orderIds.stream().collect(Collectors.toMap(orderId -> orderId, orderId -> matchingOrder));
        });
        MatchingPlanApplier planApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(eligibilityPolicy),
                mock(MatchingService.class),
                notificationService,
                Duration.ofSeconds(30),
                orderService);
        MatchingService service = new MatchingService(
                mock(MatchingEngine.class),
                notificationService,
                mock(DeliveryService.class),
                CLOCK,
                planningPolicy,
                planApplier,
                properties,
                distanceCalculator,
                new SimpleMeterRegistry(),
                mock(BoormiOfferExpirationService.class),
                orderService,
                mock(PendingOfferStateService.class));

        service.applyRegisterDreami(DREAMI_A, LOCATION);
        service.applyRegisterDreami(DREAMI_B, LOCATION);
        service.applyRegisterDreami(DREAMI_C, LOCATION);
        orderGroups(service).put(ORDER_A, group(ORDER_A, NOW.minusMinutes(10)));
        orderGroups(service).put(ORDER_B, group(ORDER_B, NOW.minusMinutes(5)));

        service.applyRunMatchingAssignmentCycle();

        List<OfferState> offers = orderGroups(service).values().stream()
                .flatMap(group -> group.offers().stream())
                .map(MatchingServicePlanningPolicyIntegrationTest::offerState)
                .sorted(Comparator.comparing(OfferState::orderId).thenComparing(OfferState::dreamiId))
                .toList();
        Map<UUID, WaitingDreamiStatus> dreamiStatuses = dreamis(service).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().status()));
        return new ServiceState(offers, dreamiStatuses);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, OrderOfferGroup> orderGroups(MatchingService service) {
        return (Map<UUID, OrderOfferGroup>) ReflectionTestUtils.getField(service, "orderOfferGroupsByOrderId");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, WaitingDreami> dreamis(MatchingService service) {
        return (Map<UUID, WaitingDreami>) ReflectionTestUtils.getField(service, "dreamiMap");
    }

    private static OrderOfferGroup group(UUID orderId, LocalDateTime matchingStartedAt) {
        return new OrderOfferGroup(
                orderId, new UUID(0, 999), LOCATION, orderSummary(orderId), List.of(), matchingStartedAt);
    }

    private static OrderSummaryDto orderSummary(UUID orderId) {
        return new OrderSummaryDto(
                orderId,
                "품목",
                null,
                OrderCd.MATCHING,
                5_000L,
                20,
                1_200L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "출발지",
                "출발지 주소",
                BigDecimal.ONE,
                BigDecimal.ONE,
                "도착지",
                "도착지 주소",
                "image",
                "문 앞",
                NOW);
    }

    private static OfferState offerState(MatchOffer offer) {
        OfferPolicySnapshot snapshot = offer.offerPolicySnapshot();
        return new OfferState(
                offer.orderId(),
                offer.dreamiId(),
                offer.status().name(),
                snapshot.scopeKey(),
                snapshot.evaluatedAt(),
                snapshot.orderWaitingSeconds(),
                snapshot.pickupDistanceMeters(),
                snapshot.maxPickupDistanceMeters());
    }

    private static MatchingPolicyProperties properties(PlanningPolicyType planningPolicyType) {
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                2,
                OfferQuotaMode.FIXED,
                2,
                planningPolicyType,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.BALANCED,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 3_000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));
    }

    private record ServiceState(List<OfferState> offers, Map<UUID, WaitingDreamiStatus> dreamiStatuses) {
    }

    private record OfferState(
            UUID orderId,
            UUID dreamiId,
            String status,
            Duration scopeKey,
            LocalDateTime evaluatedAt,
            long orderWaitingSeconds,
            double pickupDistanceMeters,
            long maxPickupDistanceMeters
    ) {
    }
}
