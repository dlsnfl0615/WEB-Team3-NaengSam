package com.naengsam.quick.domain.matching.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import com.naengsam.quick.global.session.SessionConst;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 로그인 사용자 기준 현재 매칭 상태 조회 API가 자신의 진행 중인 제안만 반환하는지 검증한다.
 */
class MatchingControllerTest {

    private static final OrderSummaryDto ORDER_SUMMARY = new OrderSummaryDto(
            UUID.randomUUID(), "품목", null, null, 5000L, 20, 1200L,
            BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), "픽업별칭", "픽업주소",
            BigDecimal.valueOf(37.2), BigDecimal.valueOf(127.2), "도착별칭", "도착주소",
            "img", LocalDateTime.now());

    private MatchingService matchingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MatchingController(matchingService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .build();
    }

    @Test
    void 진행중인_제안이_없으면_두_필드_모두_null이다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.empty());
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOffer").doesNotExist())
                .andExpect(jsonPath("$.incomingDreami").doesNotExist());
    }

    @Test
    void 드리미로서_응답_대기중인_제안이_있으면_pendingOffer로_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, userId, MatchOfferStatus.OFFERED, LocalDateTime.now());
        OrderOfferGroup group =
                new OrderOfferGroup(
                        orderId, UUID.randomUUID(), mock(GeoPoint.class), ORDER_SUMMARY, List.of(offer),
                        LocalDateTime.now());
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.of(offer));
        when(matchingService.findOrderOfferGroup(orderId)).thenReturn(Optional.of(group));
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.empty());
        when(matchingService.offerTtl()).thenReturn(Duration.ofSeconds(30));

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOffer.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.pendingOffer.expiresAt").exists())
                .andExpect(jsonPath("$.incomingDreami").doesNotExist());
    }

    @Test
    void 부르미로서_확인_대기중인_드리미_수락이_있으면_incomingDreami로_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        MatchOffer offer = new MatchOffer(
                offerId, orderId, dreamiId, MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, LocalDateTime.now());
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.empty());
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.of(offer));
        when(matchingService.pickupEtaMinutesForOffer(offer)).thenReturn(14);

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOffer").doesNotExist())
                .andExpect(jsonPath("$.incomingDreami.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.incomingDreami.dreamiId").value(dreamiId.toString()))
                .andExpect(jsonPath("$.incomingDreami.pickupEtaMinutes").value(14));
    }

    @Test
    void 드리미가_매칭엔진에_등록돼_있으면_dreamiOnline이_true다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.empty());
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.empty());
        when(matchingService.isDreamiWaiting(userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dreamiOnline").value(true));
    }

    @Test
    void 드리미가_등록돼_있지_않으면_dreamiOnline이_false다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.empty());
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.empty());
        when(matchingService.isDreamiWaiting(userId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dreamiOnline").value(false));
    }

    @Test
    void 로그인한_본인의_id로만_조회하고_다른_사용자의_id로는_조회하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(matchingService.findPendingOfferForDreami(userId)).thenReturn(Optional.empty());
        when(matchingService.findIncomingDreamiOffer(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/matching/current").sessionAttr(SessionConst.LOGIN_USER, userId))
                .andExpect(status().isOk());

        verify(matchingService).findPendingOfferForDreami(userId);
        verify(matchingService).findIncomingDreamiOffer(userId);
        verify(matchingService, never()).findPendingOfferForDreami(otherUserId);
        verify(matchingService, never()).findIncomingDreamiOffer(otherUserId);
    }
}
