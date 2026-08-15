package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.BoormiConfirmedEvent;
import com.naengsam.quick.domain.matching.event.BoormiRejectedDreamiEvent;
import com.naengsam.quick.domain.matching.event.DreamiAcceptedEvent;
import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.event.NotificationErrorPayload;
import com.naengsam.quick.domain.matching.event.OfferClosedPayload;
import com.naengsam.quick.domain.matching.event.OfferPopupPayload;
import com.naengsam.quick.domain.matching.event.OrderCancelledByBoormiEvent;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.LegacyOrderFirstAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblem;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlan;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanValidator;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scoring.OrderWaitScorePolicy;
import com.naengsam.quick.domain.matching.service.engine.Action;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.notification.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingServiceTest {

    private static final Duration OFFER_TTL = Duration.ofSeconds(30);

    // 오퍼 팝업 payload 생성에만 쓰이는 주문 표시 스냅샷. 값 자체는 대부분의 테스트에서 검증 대상이 아니다.
    private static final OrderSummaryDto ORDER_SUMMARY = new OrderSummaryDto(
            UUID.randomUUID(), "품목", null, null, 5000L, 20, 1200L,
            BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), "픽업별칭", "픽업주소",
            BigDecimal.valueOf(37.2), BigDecimal.valueOf(127.2), "도착별칭", "도착주소",
            "img", "문 앞에 놓아주세요", LocalDateTime.now());

    private MatchingService matchingService;
    private MatchingEngine matchingEngine;
    private NotificationService notificationService;
    private DeliveryService deliveryService;
    private MatchingAssignmentProblemAssembler matchingAssignmentProblemAssembler;
    private MatchingAssignmentPolicy matchingAssignmentPolicy;
    private MatchingPlanApplier matchingPlanApplier;
    private MatchingPolicyProperties matchingPolicyProperties;
    private GeoDistanceCalculator geoDistanceCalculator;

    /**
     * 예전 legacy attemptOfferRound(top-3, 오래_대기한_순)와 가장 가까운 조합. 이 조합으로 배치 매칭 사이클을 실행하면 이 파일의 기존
     * legacy 기반 테스트 기대값(오퍼 3건, 대기 오래한 순 우선 등)이 그대로 유지된다.
     */
    private static MatchingPolicyProperties matchingPolicyProperties() {
        return new MatchingPolicyProperties(
                Duration.ofMillis(500),
                3,
                OfferQuotaMode.FIXED,
                5,
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
        matchingEngine = mock(MatchingEngine.class);
        notificationService = mock(NotificationService.class);
        deliveryService = mock(DeliveryService.class);
        geoDistanceCalculator = mock(GeoDistanceCalculator.class);
        // 대부분의 테스트가 좌표 없는 mock GeoPoint를 쓰므로, assembler의 후보별 거리 계산이 실제 좌표를 요구하지 않도록 고정값을 반환한다.
        when(geoDistanceCalculator.distanceMeters(any(), any())).thenReturn(500.0);
        // 오퍼 후보 선정이 SSE liveness로 걸러지므로, 별도 명시가 없는 테스트의 드리미는 모두 연결돼 있는 것으로 둔다.
        when(notificationService.isReachableNow(any())).thenReturn(true);

        matchingPolicyProperties = matchingPolicyProperties();
        matchingAssignmentPolicy = new LegacyOrderFirstAssignmentPolicy(new OrderWaitScorePolicy());
        matchingPlanApplier = new MatchingPlanApplier(
                new MatchingPlanValidator(new LegacyOfferPolicy()), mock(MatchingService.class),
                notificationService, OFFER_TTL);
        matchingAssignmentProblemAssembler = new MatchingAssignmentProblemAssembler(
                geoDistanceCalculator, new MatchingAssignmentProblemFactory(new LegacyOfferPolicy()),
                matchingPolicyProperties, Clock.systemDefaultZone());

        matchingService = new MatchingService(
                matchingEngine, notificationService, deliveryService,
                Clock.systemDefaultZone(),
                matchingAssignmentProblemAssembler, matchingAssignmentPolicy, matchingPlanApplier, matchingPolicyProperties,
                geoDistanceCalculator, new SimpleMeterRegistry());
    }

    @Test
    void 주문을_등록하면_최대_3명의_드리미에게_제안한다() {
        // given
        UUID orderId = UUID.randomUUID();

        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        UUID dreamiId3 = UUID.randomUUID();
        UUID dreamiId4 = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyRegisterDreami(dreamiId3, location);
        matchingService.applyRegisterDreami(dreamiId4, location);

        // when (매칭 시작은 이제 오퍼를 즉시 만들지 않고 dirty만 표시하므로, 배치 사이클 대신 재매칭 스캔으로 첫 오퍼 라운드를 재현한다)
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        Map<UUID, OrderOfferGroup> orderOfferGroups =
                getOrderOfferGroups();

        List<MatchOffer> offers =
                orderOfferGroups.get(orderId).offers();

        assertThat(offers).hasSize(3);
        assertThat(offers)
                .allMatch(offer ->
                        offer.status() == MatchOfferStatus.OFFERED);

        Map<UUID, WaitingDreami> dreamiMap = getDreamiMap();

        long proposedCount = dreamiMap.values().stream()
                .filter(dreami ->
                        dreami.status() ==
                                WaitingDreamiStatus.PROPOSED)
                .count();

        long matchingCount = dreamiMap.values().stream()
                .filter(dreami ->
                        dreami.status() ==
                                WaitingDreamiStatus.MATCHING)
                .count();

        assertThat(proposedCount).isEqualTo(3);
        assertThat(matchingCount).isEqualTo(1);
    }

    @Test
    void SSE_연결이_없는_드리미는_오퍼_후보에서_제외된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID connectedDreamiId = UUID.randomUUID();
        UUID ghostDreamiId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);
        // goOffline을 못 부르고 브라우저가 죽은 드리미. dreamiMap에는 MATCHING으로 남아 있지만 SSE로는 닿을 수 없다.
        when(notificationService.isReachableNow(ghostDreamiId)).thenReturn(false);

        matchingService.applyRegisterDreami(connectedDreamiId, location);
        matchingService.applyRegisterDreami(ghostDreamiId, location);

        // when
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        List<MatchOffer> offers = getOrderOfferGroups().get(orderId).offers();

        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().dreamiId()).isEqualTo(connectedDreamiId);
        // 유령은 오퍼를 받지 않았으므로 PROPOSED로 넘어가지도, 30초 TTL을 태우지도 않는다.
        assertThat(getDreamiMap().get(ghostDreamiId).status()).isEqualTo(WaitingDreamiStatus.MATCHING);
        verify(notificationService, never()).notify(eq(ghostDreamiId), any(), any());
    }

    @Test
    void 배치_사이클은_SSE_연결이_없는_드리미를_할당_문제_입력에서_제외한다() {
        // given (이 테스트만 assembler와의 상호작용 자체를 검증해야 하므로, 공유 인스턴스 대신 mock assembler로 별도 조립한다)
        MatchingAssignmentProblemAssembler mockAssembler = mock(MatchingAssignmentProblemAssembler.class);
        when(mockAssembler.assemble(any(), any()))
                .thenReturn(new MatchingAssignmentProblem(LocalDateTime.now(), List.of(), List.of(), List.of()));
        MatchingService localMatchingService = new MatchingService(
                matchingEngine, notificationService, deliveryService,
                Clock.systemDefaultZone(),
                mockAssembler, matchingAssignmentPolicy, matchingPlanApplier, matchingPolicyProperties,
                geoDistanceCalculator, new SimpleMeterRegistry());

        UUID connectedDreamiId = UUID.randomUUID();
        UUID ghostDreamiId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);

        when(notificationService.isReachableNow(ghostDreamiId)).thenReturn(false);

        localMatchingService.applyRegisterDreami(connectedDreamiId, location);
        localMatchingService.applyRegisterDreami(ghostDreamiId, location);

        // when
        localMatchingService.applyRunMatchingAssignmentCycle();

        // then (걸러진 드리미가 problem에 아예 등장하지 않아야 정책·validator가 일관되게 동작한다)
        ArgumentCaptor<List<WaitingDreami>> dreamisCaptor = ArgumentCaptor.captor();
        verify(mockAssembler).assemble(any(), dreamisCaptor.capture());

        assertThat(dreamisCaptor.getValue())
                .extracting(WaitingDreami::dreamiId)
                .containsExactly(connectedDreamiId);
    }

    @Test
    void 드리미_한명이_수락하면_나머지_제안은_회수된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        List<MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        MatchOffer acceptedOffer = offers.getFirst();

        WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when
        matchingService.applyAcceptByDreami(acceptedOffer.offerId());

        // then
        assertThat(acceptedOffer.status())
                .isEqualTo(
                        MatchOfferStatus
                                .PENDING_BOORMI_CONFIRMATION
                );

        assertThat(offers)
                .filteredOn(offer ->
                        !offer.offerId().equals(acceptedOffer.offerId()))
                .allMatch(offer ->
                        offer.status() ==
                                MatchOfferStatus.WITHDRAWN);

        assertThat(acceptedDreami.status())
                .isEqualTo(
                        WaitingDreamiStatus.PROPOSED
                );

        assertThat(offers)
                .filteredOn(offer ->
                        !offer.offerId().equals(acceptedOffer.offerId()))
                .extracting(MatchOffer::dreamiId)
                .allMatch(dreamiId ->
                        getDreamiMap().get(dreamiId).status()
                                == WaitingDreamiStatus.MATCHING
                );
    }

    @Test
    void 부르미까지_수락하면_MATCHED가_된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyAcceptByBoormi(offer.offerId());

        // then
        assertThat(offer.status())
                .isEqualTo(MatchOfferStatus.MATCHED);

        assertThat(getOrderOfferGroups()).doesNotContainKey(orderId);
    }

    @Test
    void 부르미까지_수락하면_배달이_시작된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        MatchOffer offer = group.offers().getFirst();
        UUID boormiId = group.boormiId();
        UUID dreamiId = offer.dreamiId();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyAcceptByBoormi(offer.offerId());

        // then
        verify(deliveryService, times(1)).startDelivery(orderId, dreamiId, boormiId);
    }

    @Test
    void 이미_진행중인_방이_있으면_다시_매칭을_시작할_수_없고_기존_그룹은_유지된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);

        OrderOfferGroup originalGroup = getOrderOfferGroups().get(orderId);

        // when
        boolean started = matchingService.startMatching(order);

        // then
        assertThat(started).isFalse();
        assertThat(getOrderOfferGroups().get(orderId)).isSameAs(originalGroup);
    }

    @Test
    void WAITING_상태인_방이_있어도_다시_매칭을_시작할_수_없다() {
        // given (대기 중인 드리미가 없어 오퍼 없이 WAITING으로 생성된 그룹)
        UUID orderId = UUID.randomUUID();
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyStartMatching(order);
        OrderOfferGroup originalGroup = getOrderOfferGroups().get(orderId);
        assertThat(originalGroup.status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        // when
        boolean started = matchingService.startMatching(order);

        // then
        assertThat(started).isFalse();
        assertThat(getOrderOfferGroups().get(orderId)).isSameAs(originalGroup);
    }

    @Test
    void 대기중인_드리미가_없으면_Offer_없이_그룹이_생성되고_재매칭_대상이_된다() {
        // given
        UUID orderId = UUID.randomUUID();

        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        // when (등록된 드리미가 한 명도 없는 상태에서 매칭 시작)
        matchingService.applyStartMatching(order);

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.offers()).isEmpty();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.rematchRequired()).isTrue();
    }

    @Test
    void 대기중인_드리미가_한명이면_Offer_한개짜리_그룹이_OPEN_상태로_생성된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);

        // when (매칭 시작 직후에는 dirty만 표시되고, 재매칭 스캔이 실제 오퍼 라운드를 만든다)
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.offers()).hasSize(1);
        assertThat(group.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
        assertThat(group.offers().getFirst().status())
                .isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 드리미_한명이_여러_주문의_OrderOfferGroup에_동시에_들어갈_수_있다() {
        // 후속 커밋에서 드리미가 한 번에 하나의 방에만 참여하도록 제한할 예정.
        // 이번 커밋(OrderOfferGroup 도입) 범위에서는 아직 그 제한이 없다는 것을 명시적으로 확인한다.

        // given
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        UUID orderIdA = UUID.randomUUID();
        UUID orderIdB = UUID.randomUUID();
        Orders orderA = mock(Orders.class);
        Orders orderB = mock(Orders.class);
        when(orderA.getOrderId()).thenReturn(orderIdA);
        when(orderB.getOrderId()).thenReturn(orderIdB);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(orderA);
        matchingService.applyRunMatchingAssignmentCycle();

        // 원래라면 PROPOSED 상태라 다음 매칭 후보에서 제외되지만, 드리미 상태 제한이 아직 없다는 것을 보여주기 위해
        // 공개 API(markMatching)로 다시 MATCHING 상태로 되돌린다.
        WaitingDreami dreami = getDreamiMap().get(dreamiId);
        dreami.markMatching();

        // when
        matchingService.applyStartMatching(orderB);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        List<MatchOffer> offersA = getOrderOfferGroups().get(orderIdA).offers();
        List<MatchOffer> offersB = getOrderOfferGroups().get(orderIdB).offers();

        assertThat(offersA).extracting(MatchOffer::dreamiId).containsExactly(dreamiId);
        assertThat(offersB).extracting(MatchOffer::dreamiId).containsExactly(dreamiId);
    }

    @Test
    void Offer_상태변경은_그룹을_다시_조회해도_그대로_반영된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer acceptedOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when (수락되지 않은 나머지 오퍼가 OFFERED -> WITHDRAWN 으로 바뀜)
        matchingService.applyAcceptByDreami(acceptedOffer.offerId());

        // then
        List<MatchOffer> offersAfter =
                getOrderOfferGroups().get(orderId).offers();

        assertThat(offersAfter)
                .filteredOn(offer -> !offer.offerId().equals(acceptedOffer.offerId()))
                .allMatch(offer -> offer.status() == MatchOfferStatus.WITHDRAWN);
    }

    @Test
    void 매칭이_완료되면_인메모리_상태가_정리된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyAcceptByBoormi(offer.offerId());

        // then (그룹/오퍼/드리미가 각 맵에서 모두 제거된다)
        assertThat(getOrderOfferGroups()).doesNotContainKey(orderId);
        assertThat(getOffersById()).doesNotContainKey(offer.offerId());
        assertThat(getDreamiMap()).doesNotContainKey(dreamiId);
    }

    @Test
    void 부르미가_거절하면_그룹은_재매칭이_필요한_상태가_된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyRejectByBoormi(offer.offerId());

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.rematchRequired()).isTrue();
    }

    @Test
    void 부르미가_수락한_드리미를_거절하면_WITHDRAWN된_드리미가_다시_후보가_될_수_있다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        List<MatchOffer> offers = getOrderOfferGroups().get(orderId).offers();
        MatchOffer acceptedOffer = offers.getFirst();
        UUID withdrawnDreamiId = offers.get(1).dreamiId();

        matchingService.applyAcceptByDreami(acceptedOffer.offerId());
        assertThat(offers.get(1).status()).isEqualTo(MatchOfferStatus.WITHDRAWN);

        // when (부르미가 수락자를 거절 -> 그룹은 WAITING으로 dirty만 표시되고, 재매칭 스캔이 남은 후보로 재오퍼한다)
        matchingService.applyRejectByBoormi(acceptedOffer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.offers())
                .filteredOn(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .extracting(MatchOffer::dreamiId)
                .containsExactly(withdrawnDreamiId);
    }

    @Test
    void 모든_드리미가_거절하면_그룹은_재매칭이_필요한_상태가_된다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);

        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        List<MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        // when
        for (MatchOffer offer : offers) {
            matchingService.applyRejectByDreami(offer.offerId());
        }

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.rematchRequired()).isTrue();
    }

    @Test
    void 드리미_응답_timeout으로_만료된_오퍼는_재매칭시_같은_드리미에게_다시_제안되지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        // when (드리미 응답시간 만료 -> 재매칭을 다시 시도해도 같은 드리미는 후보에서 제외되어야 한다)
        matchingService.applyExpireDreamiOffer(offer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);
        // 만료된 오퍼는 이력(group.offers())에는 그대로 남지만, 재매칭 스캔으로도 새 오퍼가 만들어지지 않는다.
        assertThat(group.offers()).hasSize(1);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.rematchRequired()).isTrue();
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);
        // 종료된 오퍼는 신규 pending 오퍼 조회에는 포함되지 않는다.
        assertThat(matchingService.findPendingOfferForDreami(dreamiId)).isEmpty();
        // 실제로 DREAMI_EXPIRED로 전이됐으므로 만료된 offer ID로 OFFER_CLOSED를 한 번만 보낸다.
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService, times(1))
                .notify(eq(dreamiId), eq(MatchingEventType.OFFER_CLOSED), captor.capture());
        assertThat(((OfferClosedPayload) captor.getValue()).offerId()).isEqualTo(offer.offerId());
    }

    @Test
    void 만료된_offer_수락_시도는_이전_offer_ID를_담은_OFFER_ERROR로_거부된다() {
        // given (엔진이 이미 timeout으로 DREAMI_EXPIRED 처리한 오래된 offerId로 뒤늦게 수락 요청이 도착한 상황)
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer expiredOffer = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.DREAMI_EXPIRED, LocalDateTime.now());
        getOffersById().put(expiredOffer.offerId(), expiredOffer);

        // when
        matchingService.applyAcceptByDreami(expiredOffer.offerId());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(dreamiId), eq(MatchingEventType.OFFER_ERROR), captor.capture());
        NotificationErrorPayload payload = (NotificationErrorPayload) captor.getValue();
        assertThat(payload.offerId()).isEqualTo(expiredOffer.offerId());
    }

    @Test
    void 정상_신규_오퍼_수락에는_이전_오퍼의_오류나_ID가_섞이지_않는다() {
        // given (드리미 응답 timeout으로 만료된 offer-1의 이력이 남아있는 채로, 같은 드리미에게 새 offer-2가 발급된 상황)
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getBoormiId()).thenReturn(boormiId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer1 = getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyExpireDreamiOffer(offer1.offerId());

        MatchOffer offer2 = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOrderOfferGroups().get(orderId).addOffersAndOpen(List.of(offer2));
        getOffersById().put(offer2.offerId(), offer2);

        // when (새로 발급된 offer-2를 정상적으로 수락)
        matchingService.applyAcceptByDreami(offer2.offerId());

        // then (오류 알림 없이 정상 처리되고, 부르미에게 가는 정보에는 offer-2의 ID만 담긴다)
        verify(notificationService, never()).notify(any(), eq(MatchingEventType.OFFER_ERROR), any());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), captor.capture());
        DreamiInfoPayload payload = (DreamiInfoPayload) captor.getValue();
        assertThat(payload.offerId()).isEqualTo(offer2.offerId());
        assertThat(payload.offerId()).isNotEqualTo(offer1.offerId());
    }

    @Test
    void 드리미가_응답하지_않으면_DREAMI_EXPIRED가_되고_다음_대기중인_드리미에게_오퍼한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer firstOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        // 첫 오퍼가 나간 뒤에 새로 등록된 드리미가 다음 후보가 되어야 한다.
        matchingService.applyRegisterDreami(dreamiId2, location);

        // when (드리미1이 응답하지 않아 제한시간 만료 -> 그룹은 WAITING으로 dirty만 표시되고, 재매칭 스캔이 재오퍼한다)
        matchingService.applyExpireDreamiOffer(firstOffer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        assertThat(firstOffer.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);

        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.offers()).hasSize(2);
        MatchOffer secondOffer = group.offers().getLast();
        assertThat(secondOffer.offerId()).isNotEqualTo(firstOffer.offerId());
        assertThat(secondOffer.dreamiId()).isEqualTo(dreamiId2);
        assertThat(secondOffer.status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        // 만료된 첫 오퍼는 이력에는 남지만 pending 오퍼 조회에서는 제외되고, 새 오퍼만 조회된다.
        assertThat(matchingService.findPendingOfferForDreami(dreamiId1)).isEmpty();
        assertThat(matchingService.findPendingOfferForDreami(dreamiId2)).contains(secondOffer);
    }

    @Test
    void 부르미_응답_timeout으로_회수된_오퍼는_재매칭시_같은_드리미에게_다시_제안된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());

        // when (부르미 응답시간 만료 -> 드리미 본인 잘못이 아니므로 재매칭 후보에 다시 포함되어야 한다)
        matchingService.applyExpireBoormiOffer(offer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.offers()).hasSize(2);
        assertThat(group.offers().getLast().dreamiId()).isEqualTo(dreamiId);
        assertThat(group.offers().getLast().status()).isEqualTo(MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void 부르미_응답_timeout_후_드리미보다_더_오래_기다린_후보가_있으면_해당_드리미는_MATCHING_상태로_남는다() {
        // given (부르미 응답 timeout으로 자유로워질 드리미보다 먼저 등록되어 대기 시간이 더 긴 후보 3명을 준비한다)
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());

        UUID olderDreamiId1 = UUID.randomUUID();
        UUID olderDreamiId2 = UUID.randomUUID();
        UUID olderDreamiId3 = UUID.randomUUID();
        matchingService.applyRegisterDreami(olderDreamiId1, location);
        matchingService.applyRegisterDreami(olderDreamiId2, location);
        matchingService.applyRegisterDreami(olderDreamiId3, location);

        // when (부르미 응답시간 만료 -> 드리미는 MATCHING으로 돌아가지만, 대기 시간이 더 긴 다른 3명이 우선 제안받는다)
        matchingService.applyExpireBoormiOffer(offer.offerId());
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);

        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.offers())
                .filteredOn(o -> o.status() == MatchOfferStatus.OFFERED)
                .extracting(MatchOffer::dreamiId)
                .containsExactlyInAnyOrder(olderDreamiId1, olderDreamiId2, olderDreamiId3);
    }

    @Test
    void 이미_수락된_오퍼에_드리미_timeout이_뒤늦게_도착해도_상태가_바뀌지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());

        // when (드리미 응답 timeout이 이미 수락된 뒤 뒤늦게 도착)
        matchingService.applyExpireDreamiOffer(offer.offerId());

        // then (OFFERED 상태가 아니므로 무시되어야 한다 - 이벤트도 중복 발송되지 않는다)
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
        verify(notificationService, never()).notify(any(), eq(MatchingEventType.OFFER_CLOSED), any());
    }

    @Test
    void 이미_확정된_오퍼에_부르미_timeout이_뒤늦게_도착하면_무시한다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());
        matchingService.applyAcceptByBoormi(offer.offerId());

        // when (부르미 응답 timeout이 이미 MATCHED된 뒤 뒤늦게 도착 - PENDING_BOORMI_CONFIRMATION 상태가 아니므로 조용히 무시되어야 한다)
        Throwable thrown = catchThrowable(() -> matchingService.applyExpireBoormiOffer(offer.offerId()));

        // then
        assertThat(thrown).isNull();
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.MATCHED);
    }

     @Test
    void 이미_만료된_오퍼에_부르미_거절이_뒤늦게_도착하면_무시한다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());
        matchingService.applyExpireBoormiOffer(offer.offerId());

        // when (부르미 거절이 이미 BOORMI_EXPIRED된 뒤 뒤늦게 도착 - 조용히 무시되어야 한다)
        Throwable thrown = catchThrowable(() -> matchingService.applyRejectByBoormi(offer.offerId()));

        // then
        assertThat(thrown).isNull();
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.BOORMI_EXPIRED);
    }

    @Test
    void 매칭이_완료되어_정리된_그룹은_배치_매칭_사이클로_되살아나지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());
        matchingService.applyAcceptByBoormi(offer.offerId());

        assertThat(getOrderOfferGroups()).doesNotContainKey(orderId);

        // when
        Throwable thrown = catchThrowable(() -> matchingService.applyRunMatchingAssignmentCycle());

        // then
        assertThat(thrown).isNull();
        assertThat(getOrderOfferGroups()).doesNotContainKey(orderId);
    }

    @Test
    void 주문_취소로_CANCELLED된_그룹은_배치_매칭_사이클로_다시_열리지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        matchingService.applyCancelOrderByBoormi(orderId);

        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.rematchRequired()).isFalse();
        int offersBeforeScan = group.offers().size();

        // when (fallback 스케줄 재매칭 실행)
        matchingService.applyRunMatchingAssignmentCycle();

        // then (rematchRequired가 false이므로 취소된 그룹은 스캔 대상에서 제외되어야 한다)
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.offers()).hasSize(offersBeforeScan);
    }

    @Test
    void 주문_시작하면_top3_드리미에게_각각_OFFER_POPUP을_보낸다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        UUID dreamiId1 = UUID.randomUUID();
        UUID dreamiId2 = UUID.randomUUID();
        UUID dreamiId3 = UUID.randomUUID();
        UUID dreamiId4 = UUID.randomUUID();
        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyRegisterDreami(dreamiId3, location);
        matchingService.applyRegisterDreami(dreamiId4, location);

        // when (매칭 시작 후 재매칭 스캔이 첫 오퍼 라운드를 만든다)
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        ArgumentCaptor<UUID> target = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, times(3))
                .notify(target.capture(), eq(MatchingEventType.OFFER_POPUP), any());
        assertThat(target.getAllValues())
                .containsExactlyInAnyOrder(dreamiId1, dreamiId2, dreamiId3);
    }

    @Test
    void 드리미가_수락하면_주문_부르미에게_DREAMI_INFO를_보낸다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getBoormiId()).thenReturn(boormiId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        // when
        matchingService.applyAcceptByDreami(offer.offerId());

        // then
        verify(notificationService).notify(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), any());
    }

    @Test
    void 드리미_수락시_DREAMI_INFO_payload에_직선거리_기반_픽업_예상시간이_담긴다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint dreamiLocation = new GeoPoint(BigDecimal.valueOf(37.5013), BigDecimal.valueOf(127.0));
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getBoormiId()).thenReturn(boormiId);
        when(order.getOriginLatitude()).thenReturn(BigDecimal.valueOf(37.5));
        when(order.getOriginLongitude()).thenReturn(BigDecimal.valueOf(127.0));

        matchingService.applyRegisterDreami(UUID.randomUUID(), dreamiLocation);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer = getOrderOfferGroups().get(orderId).offers().getFirst();

        // when
        matchingService.applyAcceptByDreami(offer.offerId());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), captor.capture());
        DreamiInfoPayload payload = (DreamiInfoPayload) captor.getValue();
        assertThat(payload.pickupEtaMinutes()).isNotNull().isPositive();
    }

    @Test
    void 드리미_위치정보가_없으면_DREAMI_INFO_payload의_픽업_예상시간은_null이다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint dreamiLocation = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getBoormiId()).thenReturn(boormiId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), dreamiLocation);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer = getOrderOfferGroups().get(orderId).offers().getFirst();

        // when
        matchingService.applyAcceptByDreami(offer.offerId());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), captor.capture());
        DreamiInfoPayload payload = (DreamiInfoPayload) captor.getValue();
        assertThat(payload.pickupEtaMinutes()).isNull();
    }

    @Test
    void 드리미_수락시_DREAMI_INFO_payload의_만료시각은_수락시각에서_30초_뒤이다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getBoormiId()).thenReturn(boormiId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer = getOrderOfferGroups().get(orderId).offers().getFirst();

        // when
        matchingService.applyAcceptByDreami(offer.offerId());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), captor.capture());
        DreamiInfoPayload payload = (DreamiInfoPayload) captor.getValue();
        assertThat(payload.acceptedAt()).isNotNull();
        assertThat(payload.expiresAt()).isEqualTo(payload.acceptedAt().plusSeconds(30));
    }

    @Test
    void 드리미가_수락하면_선착순_패배자에게_OFFER_CLOSED를_보낸다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        List<MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();
        MatchOffer accepted = offers.getFirst();
        MatchOffer loser = offers.get(1);

        // when
        matchingService.applyAcceptByDreami(accepted.offerId());

        // then
        verify(notificationService).notify(eq(loser.dreamiId()), eq(MatchingEventType.OFFER_CLOSED), any());
    }

    @Test
    void 부르미가_거절하면_드리미에게_BOORMI_REJECTED를_보낸다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();
        MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyRejectByBoormi(offer.offerId());

        // then
        verify(notificationService).notify(eq(offer.dreamiId()), eq(MatchingEventType.BOORMI_REJECTED), any());
    }

    @Test
    void 모든_드리미가_거절한_뒤_새_드리미가_등록되면_그_드리미에게_오퍼된다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        for (MatchOffer offer : getOrderOfferGroups().get(orderId).offers()) {
            matchingService.applyRejectByDreami(offer.offerId());
        }
        assertThat(getOrderOfferGroups().get(orderId).status())
                .isEqualTo(OrderOfferGroupStatus.WAITING);

        UUID newDreamiId = UUID.randomUUID();

        // when (새 드리미 등록은 dirty만 표시하고, 재매칭 스캔이 실제 오퍼를 만든다)
        matchingService.applyRegisterDreami(newDreamiId, location);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.offers())
                .filteredOn(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .extracting(MatchOffer::dreamiId)
                .containsExactly(newDreamiId);
        assertThat(getDreamiMap().get(newDreamiId).status())
                .isEqualTo(WaitingDreamiStatus.PROPOSED);
        verify(notificationService).notify(eq(newDreamiId), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 오퍼가_소진되면_WAITING이_되고_배치_매칭_사이클이_대기_드리미에게_재오퍼한다() {
        // given (드리미 5명 중 3명만 오퍼받고 2명은 대기로 남는다)
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        for (int i = 0; i < 5; i++) {
            matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        }
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        List<UUID> firstRoundDreamis = getOrderOfferGroups().get(orderId).offers().stream()
                .map(MatchOffer::dreamiId)
                .toList();

        // when (첫 라운드 3명 전원 거절 → 그룹은 WAITING이 되고, 재매칭 스캔이 소진을 감지해 재오퍼한다)
        for (MatchOffer offer : getOrderOfferGroups().get(orderId).offers()) {
            matchingService.applyRejectByDreami(offer.offerId());
        }
        assertThat(getOrderOfferGroups().get(orderId).status()).isEqualTo(OrderOfferGroupStatus.WAITING);

        matchingService.applyRunMatchingAssignmentCycle();

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);

        List<UUID> liveOfferDreamis = group.offers().stream()
                .filter(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .map(MatchOffer::dreamiId)
                .toList();

        assertThat(liveOfferDreamis).hasSize(2);
        assertThat(liveOfferDreamis).doesNotContainAnyElementsOf(firstRoundDreamis);
    }

    @Test
    void 재오퍼는_이미_거절한_드리미를_제외한다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        MatchOffer firstOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        UUID rejectedDreamiId = firstOffer.dreamiId();
        matchingService.applyRejectByDreami(firstOffer.offerId());

        // 거절자는 다시 MATCHING 상태로 돌아오지만, 재오퍼 대상에서는 제외되어야 한다.
        assertThat(getDreamiMap().get(rejectedDreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);

        // when (다른 대기 드리미가 없으므로 재매칭 대기가 유지되어야 한다)
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        // then
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        assertThat(group.rematchRequired()).isTrue();
        assertThat(group.offers())
                .noneMatch(offer -> offer.status() == MatchOfferStatus.OFFERED);
    }

    @Test
    void 존재하지_않는_주문을_취소하면_아무_일도_일어나지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(getOrderOfferGroups()).doesNotContainKey(orderId);
        assertThat(getDreamiMap()).isEmpty();
    }

    @Test
    void 배치_대기중인_WAITING_그룹도_취소할_수_있다() {
        // given (아직 오퍼가 나가지 않은, micro-batch 대기 중인 그룹)
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(), LocalDateTime.now());
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void MATCHED_그룹을_취소해도_상태가_그대로_보존된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId,
                MatchOfferStatus.MATCHED, LocalDateTime.now());
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now());
        group.confirmMatch();
        getOrderOfferGroups().put(orderId, group);
        getDreamiMap().put(dreamiId, new WaitingDreami(
                dreamiId, mock(GeoPoint.class),
                WaitingDreamiStatus.MATCHING, LocalDateTime.now()));

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then (기존 상태가 그대로 보존되어야 한다)
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.MATCHED);
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.MATCHED);
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);
    }

    @Test
    void 모든_오퍼가_OFFERED인_상태에서_취소하면_WITHDRAWN되고_드리미는_MATCHING으로_복귀한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchOffer offerA = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchOffer offerB = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchOffer offerC = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY,
                List.of(offerA, offerB, offerC), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);
        for (UUID dreamiId : List.of(dreamiIdA, dreamiIdB, dreamiIdC)) {
            getDreamiMap().put(dreamiId, new WaitingDreami(
                    dreamiId, mock(GeoPoint.class),
                    WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));
        }

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(List.of(offerA, offerB, offerC))
                .allMatch(offer -> offer.status() == MatchOfferStatus.WITHDRAWN);
        assertThat(List.of(dreamiIdA, dreamiIdB, dreamiIdC))
                .allMatch(dreamiId -> getDreamiMap().get(dreamiId).status()
                        == WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 한명이_수락한_상태에서_부르미가_취소하면_수락자는_BOORMI_REJECTED로_나머지는_WITHDRAWN된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchOffer offerA = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        MatchOffer offerB = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchOffer offerC = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY,
                List.of(offerA, offerB, offerC), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);
        for (UUID dreamiId : List.of(dreamiIdA, dreamiIdB, dreamiIdC)) {
            getDreamiMap().put(dreamiId, new WaitingDreami(
                    dreamiId, mock(GeoPoint.class),
                    WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));
        }

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(offerA.status()).isEqualTo(MatchOfferStatus.BOORMI_REJECTED);
        assertThat(offerB.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);
        assertThat(offerC.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);
        assertThat(List.of(dreamiIdA, dreamiIdB, dreamiIdC))
                .allMatch(dreamiId -> getDreamiMap().get(dreamiId).status()
                        == WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 이미_종료된_오퍼가_섞여있으면_해당_오퍼는_그대로_유지된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchOffer offerA = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        MatchOffer offerB = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchOfferStatus.DREAMI_REJECTED, LocalDateTime.now());
        MatchOffer offerC = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchOfferStatus.DREAMI_EXPIRED, LocalDateTime.now());
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY,
                List.of(offerA, offerB, offerC), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then (처리 대상은 OFFERED/PENDING_BOORMI_CONFIRMATION 뿐이다)
        assertThat(offerA.status()).isEqualTo(MatchOfferStatus.BOORMI_REJECTED);
        assertThat(offerB.status()).isEqualTo(MatchOfferStatus.DREAMI_REJECTED);
        assertThat(offerC.status()).isEqualTo(MatchOfferStatus.DREAMI_EXPIRED);
    }

    @Test
    void 같은_주문을_두번_취소해도_두번째_호출은_아무_영향이_없다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId,
                MatchOfferStatus.OFFERED, LocalDateTime.now());
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);
        getDreamiMap().put(dreamiId, new WaitingDreami(
                dreamiId, mock(GeoPoint.class),
                WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));

        // when
        matchingService.applyCancelOrderByBoormi(orderId);
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(offer.status()).isEqualTo(MatchOfferStatus.WITHDRAWN);
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.CANCELLED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 부르미가_진행중인_방을_취소하면_엔진_큐에_CancelOrderByBoormi_액션이_제출된다() {
        // given
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);
        when(matchingEngine.submit(any())).thenReturn(true);

        // when
        boolean result = matchingService.cancelOrderByBoormi(orderId);

        // then
        assertThat(result).isTrue();
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CancelOrderByBoormi.class);
        assertThat(((CancelOrderByBoormi) captor.getValue()).orderId()).isEqualTo(orderId);
    }

    @Test
    void 취소할_진행중인_방이_없으면_큐에_제출되지_않고_false를_반환한다() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        boolean result = matchingService.cancelOrderByBoormi(orderId);

        // then
        assertThat(result).isFalse();
        verify(matchingEngine, never()).submit(any());
    }

    @Test
    void 부르미_확정_이벤트를_받으면_엔진_큐에_AcceptByBoormi_액션이_제출된다() {
        // given
        UUID offerId = UUID.randomUUID();

        // when
        matchingService.onBoormiConfirmed(new BoormiConfirmedEvent(offerId));

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AcceptByBoormi.class);
        assertThat(((AcceptByBoormi) captor.getValue()).offerId()).isEqualTo(offerId);
    }

    @Test
    void 매칭시작_요청_이벤트를_받으면_엔진_큐에_StartMatching_액션이_제출된다() {
        // given
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(UUID.randomUUID());

        // when
        matchingService.onMatchingStartRequested(new MatchingStartRequestedEvent(order));

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StartMatching.class);
        assertThat(((StartMatching) captor.getValue()).order()).isSameAs(order);
    }

    @Test
    void 부르미_주문취소_이벤트를_받으면_엔진_큐에_CancelOrderByBoormi_액션이_제출된다() {
        // given
        UUID orderId = UUID.randomUUID();
        OrderOfferGroup group = new OrderOfferGroup(
                orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(), LocalDateTime.now());
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.onOrderCancelledByBoormi(new OrderCancelledByBoormiEvent(orderId));

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CancelOrderByBoormi.class);
        assertThat(((CancelOrderByBoormi) captor.getValue()).orderId()).isEqualTo(orderId);
    }

    @Test
    void 드리미_수락_이벤트를_받으면_엔진_큐에_AcceptByDreami_액션이_제출된다() {
        // given
        UUID offerId = UUID.randomUUID();

        // when
        matchingService.onDreamiAccepted(new DreamiAcceptedEvent(offerId));

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AcceptByDreami.class);
        assertThat(((AcceptByDreami) captor.getValue()).offerId()).isEqualTo(offerId);
    }

    @Test
    void 부르미_거절_이벤트를_받으면_엔진_큐에_RejectByBoormi_액션이_제출된다() {
        // given
        UUID offerId = UUID.randomUUID();

        // when
        matchingService.onBoormiRejectedDreami(new BoormiRejectedDreamiEvent(offerId));

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RejectByBoormi.class);
        assertThat(((RejectByBoormi) captor.getValue()).offerId()).isEqualTo(offerId);
    }

    @Test
    void 배치_매칭_사이클_실행_요청은_엔진_큐에_RunMatchingAssignmentCycle_액션을_제출한다() {
        // given
        when(matchingEngine.submit(any())).thenReturn(true);

        // when
        boolean result = matchingService.runMatchingAssignmentCycle();

        // then
        assertThat(result).isTrue();
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RunMatchingAssignmentCycle.class);
    }

    @Test
    void 배치_매칭_사이클_액션은_스냅샷_조립_배정안_산출_적용을_한번에_수행한다() {
        // given (이 테스트만 조립/배정/적용 각 단계로의 위임 자체를 검증해야 하므로, 공유 인스턴스 대신 mock 협력자로 별도 조립한다)
        MatchingAssignmentProblemAssembler mockAssembler = mock(MatchingAssignmentProblemAssembler.class);
        MatchingAssignmentPolicy mockAssignmentPolicy = mock(MatchingAssignmentPolicy.class);
        MatchingPlanApplier mockPlanApplier = mock(MatchingPlanApplier.class);
        MatchingService localMatchingService = new MatchingService(
                matchingEngine, notificationService, deliveryService,
                Clock.systemDefaultZone(),
                mockAssembler, mockAssignmentPolicy, mockPlanApplier, matchingPolicyProperties,
                geoDistanceCalculator, new SimpleMeterRegistry());

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(LocalDateTime.now(), List.of(), List.of(), List.of());
        MatchingPlan plan = new MatchingPlan(List.of());
        when(mockAssembler.assemble(any(), any())).thenReturn(problem);
        when(mockAssignmentPolicy.createPlan(problem)).thenReturn(plan);

        // when
        localMatchingService.applyRunMatchingAssignmentCycle();

        // then
        ArgumentCaptor<Map<UUID, OrderOfferGroup>> groupsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<UUID, WaitingDreami>> dreamiMapCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<UUID, MatchOffer>> offersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<UUID, Set<UUID>>> offerIdsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockPlanApplier).apply(
                eq(problem), eq(plan), any(LocalDateTime.class),
                groupsCaptor.capture(), dreamiMapCaptor.capture(), offersCaptor.capture(), offerIdsCaptor.capture());
        // 배정 검증(validate)과 실제 적용(apply)이 같은 엔진 상태를 봐야 원자성이 보장되므로, 스냅샷이 아니라 살아있는 맵 참조 그대로 전달돼야 한다.
        assertThat(groupsCaptor.getValue()).isSameAs(ReflectionTestUtils.getField(localMatchingService, "orderOfferGroupsByOrderId"));
        assertThat(dreamiMapCaptor.getValue()).isSameAs(ReflectionTestUtils.getField(localMatchingService, "dreamiMap"));
        assertThat(offersCaptor.getValue()).isSameAs(ReflectionTestUtils.getField(localMatchingService, "offersById"));
        assertThat(offerIdsCaptor.getValue()).isSameAs(ReflectionTestUtils.getField(localMatchingService, "offerIdsByDreamiId"));
    }

    @Test
    void 매칭을_시작하면_즉시_오퍼를_만들지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        // when
        matchingService.applyStartMatching(order);

        // then
        OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.offers()).isEmpty();
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.WAITING);
        verify(notificationService, never()).notify(any(), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 재매칭_대상_그룹이_있으면_배치_매칭_사이클_실행시_대기중인_드리미에게_오퍼가_간다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);

        OrderOfferGroup group = new OrderOfferGroup(
                orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(), LocalDateTime.now());
        group.closeForRematch();
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
        assertThat(group.rematchRequired()).isFalse();
        assertThat(group.offers())
                .extracting(MatchOffer::dreamiId)
                .containsExactly(dreamiId);
        verify(notificationService).notify(eq(dreamiId), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 매칭을_시작하면_오퍼팝업_payload에_주문_상세정보가_담긴다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        matchingService.applyRegisterDreami(dreamiId, mock(GeoPoint.class));

        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getDeliveryAmount()).thenReturn(8000L);
        when(order.getItemName()).thenReturn("생수 2박스");
        when(order.getDeliveryEta()).thenReturn(25);
        when(order.getDeliveryDistance()).thenReturn(3200L);
        when(order.getOriginLatitude()).thenReturn(BigDecimal.valueOf(37.4979));
        when(order.getOriginLongitude()).thenReturn(BigDecimal.valueOf(127.0276));
        when(order.getOriginAlias()).thenReturn("우리집");
        when(order.getOriginAddressLine1()).thenReturn("서울시 강남구");
        when(order.getDestinationLatitude()).thenReturn(BigDecimal.valueOf(37.5445));
        when(order.getDestinationLongitude()).thenReturn(BigDecimal.valueOf(127.0559));
        when(order.getDestinationAlias()).thenReturn("회사");
        when(order.getDestinationAddressLine1()).thenReturn("서울시 성동구");
        when(order.getImageKey()).thenReturn("img-key");

        // when (매칭 시작 후 재매칭 스캔이 첫 오퍼 라운드를 만든다)
        matchingService.applyStartMatching(order);
        matchingService.applyRunMatchingAssignmentCycle();

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).notify(eq(dreamiId), eq(MatchingEventType.OFFER_POPUP), captor.capture());

        assertThat(captor.getValue()).isInstanceOf(OfferPopupPayload.class);
        OfferPopupPayload payload = (OfferPopupPayload) captor.getValue();
        assertThat(payload.orderId()).isEqualTo(orderId);
        assertThat(payload.deliveryAmount()).isEqualTo(8000L);
        assertThat(payload.itemName()).isEqualTo("생수 2박스");
        assertThat(payload.deliveryEta()).isEqualTo(25);
        assertThat(payload.deliveryDistance()).isEqualTo(3200L);
        assertThat(payload.originLatitude()).isEqualByComparingTo(BigDecimal.valueOf(37.4979));
        assertThat(payload.originLongitude()).isEqualByComparingTo(BigDecimal.valueOf(127.0276));
        assertThat(payload.originAlias()).isEqualTo("우리집");
        assertThat(payload.originAddressLine1()).isEqualTo("서울시 강남구");
        assertThat(payload.destinationLatitude()).isEqualByComparingTo(BigDecimal.valueOf(37.5445));
        assertThat(payload.destinationLongitude()).isEqualByComparingTo(BigDecimal.valueOf(127.0559));
        assertThat(payload.destinationAlias()).isEqualTo("회사");
        assertThat(payload.destinationAddressLine1()).isEqualTo("서울시 성동구");
        assertThat(payload.offeredAt()).isNotNull();
        assertThat(payload.expiresAt()).isEqualTo(payload.offeredAt().plusSeconds(30));
    }

    @Test
    void 재매칭_대상_그룹이_없으면_배치_매칭_사이클_실행시_아무일도_일어나지_않는다() {
        // given (이미 오퍼가 나가 OPEN 상태인 그룹 - WAITING이 아니므로 재매칭 대상이 아니다)
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();

        OrderOfferGroup group = new OrderOfferGroup(
                orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(), LocalDateTime.now());
        group.addOffersAndOpen(List.of());
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyRunMatchingAssignmentCycle();

        // then (대기 대상이 아니므로 상태가 그대로 보존된다)
        assertThat(group.status()).isEqualTo(OrderOfferGroupStatus.OPEN);
    }

    @Test
    void 제안의_대상_드리미와_요청한_드리미가_같으면_isDreamiOfferOwner는_true를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.isDreamiOfferOwner(offerId, dreamiId)).isTrue();
    }

    @Test
    void 제안의_대상_드리미와_요청한_드리미가_다르면_isDreamiOfferOwner는_false를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.isDreamiOfferOwner(offerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void 존재하지_않는_제안이면_isDreamiOfferOwner는_false를_반환한다() {
        assertThat(matchingService.isDreamiOfferOwner(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void OFFERED_상태이고_TTL이_지나지_않았으면_isDreamiOfferAcceptable은_true를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.isDreamiOfferAcceptable(offerId, dreamiId)).isTrue();
    }

    @Test
    void 대상_드리미가_다르면_OFFERED_상태여도_isDreamiOfferAcceptable은_false를_반환한다() {
        UUID offerId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.isDreamiOfferAcceptable(offerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void 엔진이_이미_DREAMI_EXPIRED로_만료시킨_제안은_isDreamiOfferAcceptable이_false를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);
        offer.expireByDreami(LocalDateTime.now());

        assertThat(matchingService.isDreamiOfferAcceptable(offerId, dreamiId)).isFalse();
    }

    @Test
    void OFFERED_상태여도_TTL이_지났으면_isDreamiOfferAcceptable은_false를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        LocalDateTime offeredAt = LocalDateTime.now().minus(matchingService.offerTtl()).minusSeconds(1);
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), dreamiId, MatchOfferStatus.OFFERED, offeredAt);
        getOffersById().put(offerId, offer);

        assertThat(matchingService.isDreamiOfferAcceptable(offerId, dreamiId)).isFalse();
    }

    @Test
    void 존재하지_않는_제안이면_isDreamiOfferAcceptable은_false를_반환한다() {
        assertThat(matchingService.isDreamiOfferAcceptable(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void 제안이_속한_주문의_부르미와_요청한_부르미가_같으면_isBoormiOfferOwner는_true를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);
        getOrderOfferGroups().put(orderId, new OrderOfferGroup(
                orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now()));

        assertThat(matchingService.isBoormiOfferOwner(offerId, boormiId)).isTrue();
    }

    @Test
    void 제안이_속한_주문의_부르미와_요청한_부르미가_다르면_isBoormiOfferOwner는_false를_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);
        getOrderOfferGroups().put(orderId, new OrderOfferGroup(
                orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now()));

        assertThat(matchingService.isBoormiOfferOwner(offerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void 존재하지_않는_제안이면_isBoormiOfferOwner는_false를_반환한다() {
        assertThat(matchingService.isBoormiOfferOwner(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void OFFERED_상태인_제안이_있으면_findPendingOfferForDreami가_해당_제안을_반환한다() {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), dreamiId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.findPendingOfferForDreami(dreamiId)).contains(offer);
    }

    @Test
    void 종료된_제안만_있으면_findPendingOfferForDreami는_비어있다() {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), dreamiId, MatchOfferStatus.DREAMI_REJECTED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.findPendingOfferForDreami(dreamiId)).isEmpty();
    }

    @Test
    void 다른_드리미의_제안은_findPendingOfferForDreami에_잡히지_않는다() {
        UUID offerId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, UUID.randomUUID(), UUID.randomUUID(), MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOffersById().put(offerId, offer);

        assertThat(matchingService.findPendingOfferForDreami(UUID.randomUUID())).isEmpty();
    }

    @Test
    void PENDING_BOORMI_CONFIRMATION_제안이_있으면_findIncomingDreamiOffer가_해당_제안을_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, dreamiId, MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        getOrderOfferGroups().put(orderId,
                new OrderOfferGroup(
                        orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now()));

        assertThat(matchingService.findIncomingDreamiOffer(boormiId)).contains(offer);
    }

    @Test
    void 확인_대기중인_제안이_없으면_findIncomingDreamiOffer는_비어있다() {
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, UUID.randomUUID(), MatchOfferStatus.OFFERED, LocalDateTime.now());
        getOrderOfferGroups().put(orderId,
                new OrderOfferGroup(
                        orderId, boormiId, mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer), LocalDateTime.now()));

        assertThat(matchingService.findIncomingDreamiOffer(boormiId)).isEmpty();
    }

    @Test
    void 다른_부르미의_주문은_findIncomingDreamiOffer에_잡히지_않는다() {
        UUID orderId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                UUID.randomUUID(), orderId, UUID.randomUUID(), MatchOfferStatus.PENDING_BOORMI_CONFIRMATION,
                LocalDateTime.now());
        getOrderOfferGroups().put(orderId,
                new OrderOfferGroup(
                        orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer),
                        LocalDateTime.now()));

        assertThat(matchingService.findIncomingDreamiOffer(UUID.randomUUID())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, MatchOffer> getOffersById() {
        return (Map<UUID, MatchOffer>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "offersById"
                );
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, OrderOfferGroup> getOrderOfferGroups() {
        return (Map<UUID, OrderOfferGroup>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "orderOfferGroupsByOrderId"
                );
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, WaitingDreami> getDreamiMap() {
        return (Map<UUID, WaitingDreami>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "dreamiMap"
                );
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Set<UUID>> getOfferIdsByDreamiId() {
        return (Map<UUID, Set<UUID>>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "offerIdsByDreamiId"
                );
    }
}
