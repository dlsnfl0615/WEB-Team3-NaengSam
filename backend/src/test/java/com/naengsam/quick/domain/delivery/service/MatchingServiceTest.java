package com.naengsam.quick.domain.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        Map<UUID, List<MatchingService.MatchOffer>> offersByOrderId =
                getOffersByOrderId();

        List<MatchingService.MatchOffer> offers = offersByOrderId.get(orderId);

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
                getOffersByOrderId().get(orderId);

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
                getOffersByOrderId().get(orderId).getFirst();

        MatchingService.WaitingDreami dreami =
                getDreamiMap().get(offer.dreamiId());

        matchingService.acceptByDreami(dreami, offer.offerId());

        // when
        matchingService.acceptByBoormi(offer.offerId());

        // then
        assertThat(offer.status())
                .isEqualTo(MatchingService.MatchOfferStatus.MATCHED);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<MatchingService.MatchOffer>> getOffersByOrderId() {
        return (Map<UUID, List<MatchingService.MatchOffer>>)
                ReflectionTestUtils.getField(
                        matchingService,
                        "offersByOrderId"
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
