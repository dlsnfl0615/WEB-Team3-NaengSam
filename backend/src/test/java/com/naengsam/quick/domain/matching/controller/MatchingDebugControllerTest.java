package com.naengsam.quick.domain.matching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.Order;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 매칭 디버그 엔드포인트가 요청을 올바르게 파싱해 {@link MatchingService}로 위임하는지 검증한다. 매칭 엔진 자체의 상태 전이는 {@link MatchingService} 단위 테스트에서
 * 다룬다.
 */
class MatchingDebugControllerTest {

    private MatchingService matchingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MatchingDebugController(matchingService)).build();
    }

    private MockMvc mockMvcWithExceptionHandler() {
        return MockMvcBuilders.standaloneSetup(new MatchingDebugController(matchingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 드리미_등록시_생성된_ID와_요청한_위치로_서비스에_위임한다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/debug/matching/dreamis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 37.5, \"longitude\": 127.0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<GeoPoint> locationCaptor = ArgumentCaptor.forClass(GeoPoint.class);
        verify(matchingService).registerDreami(idCaptor.capture(), locationCaptor.capture());

        assertThat(response.replace("\"", "")).isEqualTo(idCaptor.getValue().toString());
        assertThat(locationCaptor.getValue()).isEqualTo(new GeoPoint(37.5, 127.0));
    }

    @Test
    void 드리미_등록_요청마다_서로_다른_ID가_생성된다() throws Exception {
        String body = "{\"latitude\": 37.5, \"longitude\": 127.0}";

        String firstId = mockMvc.perform(post("/api/v1/debug/matching/dreamis")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String secondId = mockMvc.perform(post("/api/v1/debug/matching/dreamis")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();

        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void 드리미_등록시_본문이_깨져있으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/debug/matching/dreamis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verify(matchingService, never()).registerDreami(any(), any());
    }

    @Test
    void 드리미_제거시_경로변수의_ID로_서비스에_위임한다() throws Exception {
        UUID dreamiId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/debug/matching/dreamis/{dreamiId}", dreamiId))
                .andExpect(status().isOk());

        verify(matchingService).removeDreami(dreamiId);
    }

    @Test
    void 드리미_제거시_UUID_형식이_아니면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/debug/matching/dreamis/{dreamiId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(matchingService, never()).removeDreami(any());
    }

    @Test
    void 등록후_조회하면_대기중_드리미_목록에_나타난다() throws Exception {
        UUID dreamiId = UUID.randomUUID();
        GeoPoint location = new GeoPoint(37.5, 127.0);
        MatchingService.WaitingDreami waitingDreami = new MatchingService.WaitingDreami(
                dreamiId, location, MatchingService.WaitingDreamiStatus.MATCHING, LocalDateTime.now());
        when(matchingService.waitingDreamis()).thenReturn(List.of(waitingDreami));

        mockMvc.perform(get("/api/v1/debug/matching/dreamis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dreamiId").value(dreamiId.toString()))
                .andExpect(jsonPath("$[0].location.latitude").value(37.5))
                .andExpect(jsonPath("$[0].status").value("MATCHING"));
    }

    @Test
    void 제거후_조회하면_대기중_드리미_목록에서_사라진다() throws Exception {
        when(matchingService.waitingDreamis()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/debug/matching/dreamis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 매칭_시작시_경로의_orderId와_바디로_Order를_구성해_위임한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();

        MatchingService.OrderOfferGroup group =
                new MatchingService.OrderOfferGroup(orderId, List.of());
        when(matchingService.findOrderOfferGroup(orderId)).thenReturn(Optional.of(group));

        mockMvc.perform(post("/api/v1/debug/matching/orders/{orderId}/start", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boormiId": "%s", "destination": {"latitude": 37.5, "longitude": 127.0}}
                                """.formatted(boormiId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.rematchRequired").value(false));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(matchingService).startMatching(orderCaptor.capture());

        assertThat(orderCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(orderCaptor.getValue().boormiId()).isEqualTo(boormiId);
        assertThat(orderCaptor.getValue().destination()).isEqualTo(new GeoPoint(37.5, 127.0));
    }

    @Test
    void 매칭_시작시_필수값이_없으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/debug/matching/orders/{orderId}/start", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(matchingService, never()).startMatching(any());
    }

    @Test
    void 이미_진행중인_매칭이면_409를_반환한다() throws Exception {
        doThrow(new BusinessException(GeneralErrorCode.CONFLICT)).when(matchingService).startMatching(any());

        mockMvcWithExceptionHandler().perform(post("/api/v1/debug/matching/orders/{orderId}/start", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boormiId": "%s", "destination": {"latitude": 0, "longitude": 0}}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    void 그룹_조회시_존재하면_그룹_정보를_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(30);

        MatchingService.MatchOffer offer = new MatchingService.MatchOffer(
                offerId, orderId, dreamiId, MatchingService.MatchOfferStatus.OFFERED, expiresAt);
        MatchingService.OrderOfferGroup group =
                new MatchingService.OrderOfferGroup(orderId, List.of(offer));
        when(matchingService.findOrderOfferGroup(orderId)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/v1/debug/matching/orders/{orderId}/group", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.rematchRequired").value(false))
                .andExpect(jsonPath("$.offers[0].offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.offers[0].dreamiId").value(dreamiId.toString()))
                .andExpect(jsonPath("$.offers[0].status").value("OFFERED"));
    }

    @Test
    void 그룹_조회시_존재하지_않으면_404를_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(matchingService.findOrderOfferGroup(orderId)).thenReturn(Optional.empty());

        mockMvcWithExceptionHandler().perform(get("/api/v1/debug/matching/orders/{orderId}/group", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_005"));
    }

    @Test
    void 드리미_수락_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/dreami-accept", offerId))
                .andExpect(status().isOk());

        verify(matchingService).acceptByDreami(offerId);
    }

    @Test
    void 드리미_거절_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/dreami-reject", offerId))
                .andExpect(status().isOk());

        verify(matchingService).rejectByDreami(offerId);
    }

    @Test
    void 부르미_수락_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/boormi-accept", offerId))
                .andExpect(status().isOk());

        verify(matchingService).acceptByBoormi(offerId);
    }

    @Test
    void 부르미_거절_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/boormi-reject", offerId))
                .andExpect(status().isOk());

        verify(matchingService).rejectByBoormi(offerId);
    }

    @Test
    void 드리미_만료_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/dreami-expire", offerId))
                .andExpect(status().isOk());

        verify(matchingService).expireDreamiOffer(offerId);
    }

    @Test
    void 부르미_만료_요청은_offerId로_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/debug/matching/offers/{offerId}/boormi-expire", offerId))
                .andExpect(status().isOk());

        verify(matchingService).expireBoormiOffer(offerId);
    }
}
