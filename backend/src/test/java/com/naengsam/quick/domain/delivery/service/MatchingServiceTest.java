package com.naengsam.quick.domain.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.delivery.dto.GeoPoint;
import com.naengsam.quick.domain.delivery.dto.Order;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingServiceTest {

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        matchingService = new MatchingService(matchingEngine);
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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId1, location);
        matchingService.applyRegisterDreami(dreamiId2, location);
        matchingService.applyRegisterDreami(dreamiId3, location);
        matchingService.applyRegisterDreami(dreamiId4, location);

        // when
        matchingService.startMatching(order);

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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.startMatching(order);

        List<MatchingService.MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        MatchingService.MatchOffer acceptedOffer = offers.getFirst();

        MatchingService.WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when
        matchingService.acceptByDreami(
                acceptedDreami,
                acceptedOffer.offerId()
        );

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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.startMatching(order);

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        MatchingService.WaitingDreami dreami =
                getDreamiMap().get(offer.dreamiId());

        matchingService.acceptByDreami(dreami, offer.offerId());

        // when
        matchingService.acceptByBoormi(offer.offerId());

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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.startMatching(order);

        MatchingService.OrderOfferGroup originalGroup = getOrderOfferGroups().get(orderId);

        // when
        Throwable thrown = catchThrowable(() -> matchingService.startMatching(order));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(getOrderOfferGroups().get(orderId)).isSameAs(originalGroup);
    }

    @Test
    void 대기중인_드리미가_없으면_Offer_없이_그룹이_생성되고_재매칭_대상이_된다() {
        // given
        UUID orderId = UUID.randomUUID();

        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        // when (등록된 드리미가 한 명도 없는 상태에서 매칭 시작)
        matchingService.startMatching(order);

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
        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(dreamiId, location);

        // when
        matchingService.startMatching(order);

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
        Order orderA = mock(Order.class);
        Order orderB = mock(Order.class);
        when(orderA.orderId()).thenReturn(orderIdA);
        when(orderB.orderId()).thenReturn(orderIdB);

        matchingService.applyRegisterDreami(dreamiId, location);
        matchingService.startMatching(orderA);

        // 원래라면 PROPOSED 상태라 다음 매칭 후보에서 제외되지만, 드리미 상태 제한이 아직 없다는 것을 보여주기 위해
        // 공개 API(changeStatus)로 다시 MATCHING 상태로 되돌린다.
        MatchingService.WaitingDreami dreami = getDreamiMap().get(dreamiId);
        dreami.changeStatus(MatchingService.WaitingDreamiStatus.MATCHING);

        // when
        matchingService.startMatching(orderB);

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
        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.startMatching(order);

        MatchingService.MatchOffer acceptedOffer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        MatchingService.WaitingDreami acceptedDreami =
                getDreamiMap().get(acceptedOffer.dreamiId());

        // when (수락되지 않은 나머지 오퍼가 OFFERED -> WITHDRAWN 으로 바뀜)
        matchingService.acceptByDreami(acceptedDreami, acceptedOffer.offerId());

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
        Order order = mock(Order.class);
        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.startMatching(order);

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();
        MatchingService.WaitingDreami dreami = getDreamiMap().get(offer.dreamiId());

        matchingService.acceptByDreami(dreami, offer.offerId());

        // when
        matchingService.acceptByBoormi(offer.offerId());

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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.startMatching(order);

        MatchingService.MatchOffer offer =
                getOrderOfferGroups().get(orderId).offers().getFirst();

        MatchingService.WaitingDreami dreami =
                getDreamiMap().get(offer.dreamiId());

        matchingService.acceptByDreami(dreami, offer.offerId());

        // when
        matchingService.rejectByBoormi(offer.offerId());

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
        Order order = mock(Order.class);

        when(order.orderId()).thenReturn(orderId);

        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);
        matchingService.applyRegisterDreami(UUID.randomUUID(), location);

        matchingService.startMatching(order);

        List<MatchingService.MatchOffer> offers =
                getOrderOfferGroups().get(orderId).offers();

        // when
        for (MatchingService.MatchOffer offer : offers) {
            MatchingService.WaitingDreami dreami = getDreamiMap().get(offer.dreamiId());
            matchingService.rejectByDreami(dreami, offer.offerId());
        }

        // then
        MatchingService.OrderOfferGroup group = getOrderOfferGroups().get(orderId);

        assertThat(group.status()).isEqualTo(MatchingService.OrderOfferGroupStatus.CLOSED);
        assertThat(group.rematchRequired()).isTrue();
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
