package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.sse.SseService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingServiceTest {

    private MatchingService matchingService;
    private MatchingEngine matchingEngine;
    private SseService sseService;
    private OfferTimeoutScheduler offerTimeoutScheduler;

    @BeforeEach
    void setUp() {
        matchingEngine = mock(MatchingEngine.class);
        sseService = mock(SseService.class);
        offerTimeoutScheduler = mock(OfferTimeoutScheduler.class);
        matchingService = new MatchingService(matchingEngine, sseService, offerTimeoutScheduler);
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

        // when
        matchingService.applyStartMatching(order);

        // then
        Map<UUID, MatchingService.OrderOfferGroup> orderOfferGroups =
                getOrderOfferGroups();

        List<MatchingService.MatchOffer> offers =
                orderOfferGroups.get(orderId).offers();

        assertThat(offers).hasSize(3);
        assertThat(offers)
                .allMatch(offer ->
                        offer.status() == MatchingService.MatchOfferStatus.OFFERED);

        Map<UUID, MatchingService.WaitingDreami> dreamiMap = getDreamiMap();

        long proposedCount = dreamiMap.values().stream()
                .filter(dreami ->
                        dreami.status() ==
                                MatchingService.WaitingDreamiStatus.PROPOSED)
                .count();

        long matchingCount = dreamiMap.values().stream()
                .filter(dreami ->
                        dreami.status() ==
                                MatchingService.WaitingDreamiStatus.MATCHING)
                .count();

        assertThat(proposedCount).isEqualTo(3);
        assertThat(matchingCount).isEqualTo(1);
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

        List<MatchingService.MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        MatchingService.MatchOffer acceptedOffer = offers.getFirst();

        MatchingService.WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when
        matchingService.applyAcceptByDreami(acceptedOffer.offerId());

        // then
        assertThat(acceptedOffer.status())
                .isEqualTo(
                        MatchingService.MatchOfferStatus
                                .PENDING_BOORMI_CONFIRMATION
                );

        assertThat(offers)
                .filteredOn(offer ->
                        !offer.offerId().equals(acceptedOffer.offerId()))
                .allMatch(offer ->
                        offer.status() ==
                                MatchingService.MatchOfferStatus.WITHDRAWN);

        assertThat(acceptedDreami.status())
                .isEqualTo(
                        MatchingService.WaitingDreamiStatus.PROPOSED
                );

        assertThat(offers)
                .filteredOn(offer ->
                        !offer.offerId().equals(acceptedOffer.offerId()))
                .extracting(MatchingService.MatchOffer::dreamiId)
                .allMatch(dreamiId ->
                        getDreamiMap().get(dreamiId).status()
                                == MatchingService.WaitingDreamiStatus.MATCHING
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

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyAcceptByBoormi(offer.offerId());

        // then
        assertThat(offer.status())
                .isEqualTo(MatchingService.MatchOfferStatus.MATCHED);

        assertThat(getOrderOfferGroups().get(orderId).status())
                .isEqualTo(MatchingService.OrderOfferGroupStatus.MATCHED);
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

        MatchingService.OrderOfferGroup originalGroup = getOrderOfferGroups().get(orderId);

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
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.offers()).isEmpty();
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
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

        // when
        matchingService.applyStartMatching(order);

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.offers()).hasSize(1);
        assertThat(group.offers().getFirst().dreamiId()).isEqualTo(dreamiId);
        assertThat(group.offers().getFirst().status())
                .isEqualTo(MatchingService.MatchOfferStatus.OFFERED);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.OPEN);
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

        // 원래라면 PROPOSED 상태라 다음 매칭 후보에서 제외되지만, 드리미 상태 제한이 아직 없다는 것을 보여주기 위해
        // 공개 API(markMatching)로 다시 MATCHING 상태로 되돌린다.
        MatchingService.WaitingDreami dreami = getDreamiMap().get(dreamiId);
        dreami.markMatching();

        // when
        matchingService.applyStartMatching(orderB);

        // then
        List<MatchingService.MatchOffer> offersA = getOrderOfferGroups().get(orderIdA).offers();
        List<MatchingService.MatchOffer> offersB = getOrderOfferGroups().get(orderIdB).offers();

        assertThat(offersA).extracting(MatchingService.MatchOffer::dreamiId).containsExactly(dreamiId);
        assertThat(offersB).extracting(MatchingService.MatchOffer::dreamiId).containsExactly(dreamiId);
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

        MatchingService.MatchOffer acceptedOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        MatchingService.WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when (수락되지 않은 나머지 오퍼가 OFFERED -> WITHDRAWN 으로 바뀜)
        matchingService.applyAcceptByDreami(acceptedOffer.offerId());

        // then
        List<MatchingService.MatchOffer> offersAfter =
                getOrderOfferGroups().get(orderId).offers();

        assertThat(offersAfter)
                .filteredOn(offer -> !offer.offerId().equals(acceptedOffer.offerId()))
                .allMatch(offer -> offer.status() == MatchingService.MatchOfferStatus.WITHDRAWN);
    }

    @Test
    void 매칭이_완료되면_그룹은_제거되지_않고_MATCHED_상태로_남는다() {
        // given
        UUID orderId = UUID.randomUUID();

        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyStartMatching(order);

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyAcceptByBoormi(offer.offerId());

        // then (그룹이 map에서 삭제되지 않고, 종료 상태로만 남는다)
        assertThat(getOrderOfferGroups()).containsKey(orderId);
        assertThat(getOrderOfferGroups().get(orderId).status())
                .isEqualTo(MatchingService.OrderOfferGroupStatus.MATCHED);
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

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyRejectByBoormi(offer.offerId());

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isTrue();
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

        List<MatchingService.MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        // when
        for (MatchingService.MatchOffer offer : offers) {
            matchingService.applyRejectByDreami(offer.offerId());
        }

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isTrue();
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

        // when
        matchingService.applyStartMatching(order);

        // then
        ArgumentCaptor<UUID> target = ArgumentCaptor.forClass(UUID.class);
        verify(sseService, times(3))
                .send(target.capture(), eq(MatchingEventType.OFFER_POPUP), any());
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
        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        // when
        matchingService.applyAcceptByDreami(offer.offerId());

        // then
        verify(sseService).send(eq(boormiId), eq(MatchingEventType.DREAMI_INFO), any());
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
        List<MatchingService.MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();
        MatchingService.MatchOffer accepted = offers.getFirst();
        MatchingService.MatchOffer loser = offers.get(1);

        // when
        matchingService.applyAcceptByDreami(accepted.offerId());

        // then
        verify(sseService).send(eq(loser.dreamiId()), eq(MatchingEventType.OFFER_CLOSED), any());
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
        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        matchingService.applyAcceptByDreami(offer.offerId());

        // when
        matchingService.applyRejectByBoormi(offer.offerId());

        // then
        verify(sseService).send(eq(offer.dreamiId()), eq(MatchingEventType.BOORMI_REJECTED), any());
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

        for (MatchingService.MatchOffer offer : getOrderOfferGroups().get(orderId).offers()) {
            matchingService.applyRejectByDreami(offer.offerId());
        }
        assertThat(getOrderOfferGroups().get(orderId).status())
                .isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);

        UUID newDreamiId = UUID.randomUUID();

        // when
        matchingService.applyRegisterDreami(newDreamiId, location);

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.OPEN);
        assertThat(group.offers())
                .filteredOn(offer -> offer.status() == MatchingService.MatchOfferStatus.OFFERED)
                .extracting(MatchingService.MatchOffer::dreamiId)
                .containsExactly(newDreamiId);
        assertThat(getDreamiMap().get(newDreamiId).status())
                .isEqualTo(MatchingService.WaitingDreamiStatus.PROPOSED);
        verify(sseService).send(eq(newDreamiId), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 오퍼가_소진되면_아직_제안받지_않은_대기_드리미에게_즉시_재오퍼된다() {
        // given (드리미 5명 중 3명만 오퍼받고 2명은 대기로 남는다)
        UUID orderId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);
        Orders order = mock(Orders.class);
        when(order.getOrderId()).thenReturn(orderId);

        for (int i = 0; i < 5; i++) {
            matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        }
        matchingService.applyStartMatching(order);

        List<UUID> firstRoundDreamis = getOrderOfferGroups().get(orderId).offers().stream()
                .map(MatchingService.MatchOffer::dreamiId)
                .toList();

        // when (첫 라운드 3명 전원 거절 → 소진 즉시 재오퍼)
        for (MatchingService.MatchOffer offer : getOrderOfferGroups().get(orderId).offers()) {
            matchingService.applyRejectByDreami(offer.offerId());
        }

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.OPEN);

        List<UUID> liveOfferDreamis = group.offers().stream()
                .filter(offer -> offer.status() == MatchingService.MatchOfferStatus.OFFERED)
                .map(MatchingService.MatchOffer::dreamiId)
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

        MatchingService.MatchOffer firstOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        UUID rejectedDreamiId = firstOffer.dreamiId();
        matchingService.applyRejectByDreami(firstOffer.offerId());

        // 거절자는 다시 MATCHING 상태로 돌아오지만, 재오퍼 대상에서는 제외되어야 한다.
        assertThat(getDreamiMap().get(rejectedDreamiId).status())
                .isEqualTo(MatchingService.WaitingDreamiStatus.MATCHING);

        // when (다른 대기 드리미가 없으므로 재매칭 대기가 유지되어야 한다)
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        // then
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isTrue();
        assertThat(group.offers())
                .noneMatch(offer -> offer.status() == MatchingService.MatchOfferStatus.OFFERED);
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
    void OPEN이_아닌_그룹을_취소해도_상태가_그대로_보존된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        MatchingService.MatchOffer offer = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiId,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.OrderOfferGroup group =
                new MatchingService.OrderOfferGroup(orderId, UUID.randomUUID(), List.of(offer));
        group.closeForRematch();
        getOrderOfferGroups().put(orderId, group);
        getDreamiMap().put(dreamiId, new MatchingService.WaitingDreami(
                dreamiId, mock(GeoPoint.class),
                MatchingService.WaitingDreamiStatus.MATCHING, LocalDateTime.now()));

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then (기존 상태가 그대로 보존되어야 한다)
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isTrue();
        assertThat(offer.status()).isEqualTo(MatchingService.MatchOfferStatus.OFFERED);
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(MatchingService.WaitingDreamiStatus.MATCHING);
    }

    @Test
    void 모든_오퍼가_OFFERED인_상태에서_취소하면_WITHDRAWN되고_드리미는_MATCHING으로_복귀한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchingService.MatchOffer offerA = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.MatchOffer offerB = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.MatchOffer offerC = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.OrderOfferGroup group = new MatchingService.OrderOfferGroup(
                orderId, UUID.randomUUID(), List.of(offerA, offerB, offerC));
        getOrderOfferGroups().put(orderId, group);
        for (UUID dreamiId : List.of(dreamiIdA, dreamiIdB, dreamiIdC)) {
            getDreamiMap().put(dreamiId, new MatchingService.WaitingDreami(
                    dreamiId, mock(GeoPoint.class),
                    MatchingService.WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));
        }

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(List.of(offerA, offerB, offerC))
                .allMatch(offer -> offer.status() == MatchingService.MatchOfferStatus.WITHDRAWN);
        assertThat(List.of(dreamiIdA, dreamiIdB, dreamiIdC))
                .allMatch(dreamiId -> getDreamiMap().get(dreamiId).status()
                        == MatchingService.WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 한명이_수락한_상태에서_부르미가_취소하면_수락자는_BOORMI_REJECTED로_나머지는_WITHDRAWN된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchingService.MatchOffer offerA = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchingService.MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        MatchingService.MatchOffer offerB = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.MatchOffer offerC = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.OrderOfferGroup group = new MatchingService.OrderOfferGroup(
                orderId, UUID.randomUUID(), List.of(offerA, offerB, offerC));
        getOrderOfferGroups().put(orderId, group);
        for (UUID dreamiId : List.of(dreamiIdA, dreamiIdB, dreamiIdC)) {
            getDreamiMap().put(dreamiId, new MatchingService.WaitingDreami(
                    dreamiId, mock(GeoPoint.class),
                    MatchingService.WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));
        }

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(offerA.status()).isEqualTo(MatchingService.MatchOfferStatus.BOORMI_REJECTED);
        assertThat(offerB.status()).isEqualTo(MatchingService.MatchOfferStatus.WITHDRAWN);
        assertThat(offerC.status()).isEqualTo(MatchingService.MatchOfferStatus.WITHDRAWN);
        assertThat(List.of(dreamiIdA, dreamiIdB, dreamiIdC))
                .allMatch(dreamiId -> getDreamiMap().get(dreamiId).status()
                        == MatchingService.WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 이미_종료된_오퍼가_섞여있으면_해당_오퍼는_그대로_유지된다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiIdA = UUID.randomUUID();
        UUID dreamiIdB = UUID.randomUUID();
        UUID dreamiIdC = UUID.randomUUID();

        MatchingService.MatchOffer offerA = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdA,
                MatchingService.MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        MatchingService.MatchOffer offerB = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdB,
                MatchingService.MatchOfferStatus.DREAMI_REJECTED, LocalDateTime.now());
        MatchingService.MatchOffer offerC = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiIdC,
                MatchingService.MatchOfferStatus.DREAMI_EXPIRED, LocalDateTime.now());
        MatchingService.OrderOfferGroup group = new MatchingService.OrderOfferGroup(
                orderId, UUID.randomUUID(), List.of(offerA, offerB, offerC));
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyCancelOrderByBoormi(orderId);

        // then (처리 대상은 OFFERED/PENDING_BOORMI_CONFIRMATION 뿐이다)
        assertThat(offerA.status()).isEqualTo(MatchingService.MatchOfferStatus.BOORMI_REJECTED);
        assertThat(offerB.status()).isEqualTo(MatchingService.MatchOfferStatus.DREAMI_REJECTED);
        assertThat(offerC.status()).isEqualTo(MatchingService.MatchOfferStatus.DREAMI_EXPIRED);
    }

    @Test
    void 같은_주문을_두번_취소해도_두번째_호출은_아무_영향이_없다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        MatchingService.MatchOffer offer = new MatchingService.MatchOffer(
                UUID.randomUUID(), orderId, dreamiId,
                MatchingService.MatchOfferStatus.OFFERED, LocalDateTime.now());
        MatchingService.OrderOfferGroup group = new MatchingService.OrderOfferGroup(
                orderId, UUID.randomUUID(), List.of(offer));
        getOrderOfferGroups().put(orderId, group);
        getDreamiMap().put(dreamiId, new MatchingService.WaitingDreami(
                dreamiId, mock(GeoPoint.class),
                MatchingService.WaitingDreamiStatus.PROPOSED, LocalDateTime.now()));

        // when
        matchingService.applyCancelOrderByBoormi(orderId);
        matchingService.applyCancelOrderByBoormi(orderId);

        // then
        assertThat(offer.status()).isEqualTo(MatchingService.MatchOfferStatus.WITHDRAWN);
        assertThat(getDreamiMap().get(dreamiId).status())
                .isEqualTo(MatchingService.WaitingDreamiStatus.MATCHING);
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isFalse();
    }

    @Test
    void 부르미가_주문을_취소하면_엔진_큐에_CancelOrderByBoormi_액션이_제출된다() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        matchingService.cancelOrderByBoormi(orderId);

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CancelOrderByBoormi.class);
        assertThat(((CancelOrderByBoormi) captor.getValue()).orderId()).isEqualTo(orderId);
    }

    @Test
    void 스케줄된_재매칭_트리거는_엔진_큐에_RematchWaitingGroups_액션을_제출한다() {
        // when
        matchingService.scheduleRematchWaitingGroups();

        // then
        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(matchingEngine).submit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RematchWaitingGroups.class);
    }

    @Test
    void 재매칭_대상_그룹이_있으면_스케줄된_재매칭_실행시_대기중인_드리미에게_오퍼가_간다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        GeoPoint location = mock(GeoPoint.class);

        matchingService.applyRegisterDreami(dreamiId, location);

        MatchingService.OrderOfferGroup group =
                new MatchingService.OrderOfferGroup(orderId, boormiId, List.of());
        group.closeForRematch();
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyRematchWaitingGroups();

        // then
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.OPEN);
        assertThat(group.rematchRequired()).isFalse();
        assertThat(group.offers())
                .extracting(MatchingService.MatchOffer::dreamiId)
                .containsExactly(dreamiId);
        verify(sseService).send(eq(dreamiId), eq(MatchingEventType.OFFER_POPUP), any());
    }

    @Test
    void 재매칭_대상_그룹이_없으면_스케줄된_재매칭_실행시_아무일도_일어나지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();

        MatchingService.OrderOfferGroup group =
                new MatchingService.OrderOfferGroup(orderId, boormiId, List.of());
        group.markMatched();
        getOrderOfferGroups().put(orderId, group);

        // when
        matchingService.applyRematchWaitingGroups();

        // then (대기 대상이 아니므로 상태가 그대로 보존된다)
        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.MATCHED);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, MatchingService.OrderOfferGroup> getOrderOfferGroups() {
        return (Map<UUID, MatchingService.OrderOfferGroup>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "orderOfferGroupsByOrderId"
                );
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, MatchingService.WaitingDreami> getDreamiMap() {
        return (Map<UUID, MatchingService.WaitingDreami>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "dreamiMap"
                );
    }
}
