package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.service.MatchingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingAssignmentProblemAssembler가 엔진 상태(OrderOfferGroup/WaitingDreami)에서 상태 필터링·거리 계산· 이전 오퍼 이력 조회를 거쳐
 * MatchingAssignmentProblemFactory에 넘길 입력을 올바르게 만드는지 확인한다.
 */
class MatchingAssignmentProblemAssemblerTest {

    private static final GeoPoint ORDER_LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final GeoPoint DREAMI_LOCATION = new GeoPoint(BigDecimal.ONE, BigDecimal.ONE);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 10, 12, 0);
    private static final Clock CLOCK =
            Clock.fixed(EVALUATED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final double DISTANCE_METERS = 500.0;

    private MatchingService matchingService;
    private BoormiService boormiService;
    private MatchingAssignmentProblemAssembler assembler;

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
        return new MatchingPolicyProperties(
                Duration.ofMillis(200),
                3,
                AssignmentPolicyType.LEGACY_ORDER_FIRST,
                ScoringPolicyType.ORDER_WAIT,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(Duration.ofMinutes(5), Duration.ofMinutes(10),
                        Duration.ofMinutes(3)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 1000, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        boormiService = mock(BoormiService.class);
        when(boormiService.distanceMeters(any(), any())).thenReturn(DISTANCE_METERS);

        MatchingPolicyProperties properties = matchingPolicyProperties();
        MatchingAssignmentProblemFactory factory = new MatchingAssignmentProblemFactory(new LegacyOfferPolicy());
        assembler = new MatchingAssignmentProblemAssembler(matchingService, boormiService, factory, properties, CLOCK);
    }

    @Test
    void WAITING_주문만_MatchingOrderInput으로_변환되고_나머지_상태는_제외된다() {
        UUID waitingOrderId = UUID.randomUUID();
        when(matchingService.orderOfferGroups()).thenReturn(List.of(
                group(waitingOrderId, OrderOfferGroupStatus.WAITING),
                group(UUID.randomUUID(), OrderOfferGroupStatus.OPEN),
                group(UUID.randomUUID(), OrderOfferGroupStatus.MATCHED),
                group(UUID.randomUUID(), OrderOfferGroupStatus.CANCELLED)
        ));
        when(matchingService.waitingDreamis()).thenReturn(List.of());

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(waitingOrderId);
    }

    @Test
    void MATCHING_드리미만_MatchingDreamiInput으로_변환되고_PROPOSED는_제외된다() {
        UUID matchingDreamiId = UUID.randomUUID();
        when(matchingService.orderOfferGroups()).thenReturn(List.of());
        when(matchingService.waitingDreamis()).thenReturn(List.of(
                dreami(matchingDreamiId, WaitingDreamiStatus.MATCHING),
                dreami(UUID.randomUUID(), WaitingDreamiStatus.PROPOSED)
        ));

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.dreamis()).extracting(MatchingDreamiInput::dreamiId).containsExactly(matchingDreamiId);
    }

    @Test
    void 주문과_드리미_사이의_거리를_계산해서_후보에_담는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.orderOfferGroups())
                .thenReturn(List.of(group(orderId, OrderOfferGroupStatus.WAITING)));
        when(matchingService.waitingDreamis())
                .thenReturn(List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING)));

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.candidates()).hasSize(1);
        assertThat(problem.candidates().get(0).distanceMeters()).isEqualTo(DISTANCE_METERS);
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
        when(matchingService.orderOfferGroups())
                .thenReturn(List.of(new OrderOfferGroup(
                        orderId, UUID.randomUUID(), ORDER_LOCATION, null, offers, EVALUATED_AT.minusHours(1))));
        when(matchingService.waitingDreamis())
                .thenReturn(List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING)));

        MatchingAssignmentProblem problem = assembler.assemble();

        PreviousOfferInteraction interaction = problem.candidates().get(0).previousInteraction().orElseThrow();
        assertThat(interaction.outcome()).isEqualTo(PreviousOfferOutcome.BOORMI_EXPIRED);
        assertThat(interaction.occurredAt()).isEqualTo(EVALUATED_AT.minusMinutes(10));
    }

    @Test
    void 이전_오퍼_이력이_없으면_Optional_empty이다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.orderOfferGroups())
                .thenReturn(List.of(group(orderId, OrderOfferGroupStatus.WAITING)));
        when(matchingService.waitingDreamis())
                .thenReturn(List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING)));

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.candidates().get(0).previousInteraction()).isEmpty();
    }

    @Test
    void 대기_시작_시각이_평가_시각보다_미래면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        when(matchingService.orderOfferGroups())
                .thenReturn(List.of(new OrderOfferGroup(
                        orderId, UUID.randomUUID(), ORDER_LOCATION, null, List.of(), EVALUATED_AT.plusMinutes(1))));
        when(matchingService.waitingDreamis()).thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> assembler.assemble());

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문이나_드리미가_없어도_허용된다() {
        when(matchingService.orderOfferGroups()).thenReturn(List.of());
        when(matchingService.waitingDreamis()).thenReturn(List.of());

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.orders()).isEmpty();
        assertThat(problem.dreamis()).isEmpty();
        assertThat(problem.candidates()).isEmpty();
    }

    @Test
    void 생성한_입력을_MatchingAssignmentProblemFactory에_전달해_결과를_만든다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.orderOfferGroups())
                .thenReturn(List.of(group(orderId, OrderOfferGroupStatus.WAITING)));
        when(matchingService.waitingDreamis())
                .thenReturn(List.of(dreami(dreamiId, WaitingDreamiStatus.MATCHING)));

        MatchingAssignmentProblem problem = assembler.assemble();

        assertThat(problem.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(problem.orders()).extracting(MatchingOrderInput::orderId).containsExactly(orderId);
        assertThat(problem.dreamis()).extracting(MatchingDreamiInput::dreamiId).containsExactly(dreamiId);
        assertThat(problem.candidates()).extracting(candidate -> candidate.orderId()).containsExactly(orderId);
    }
}
