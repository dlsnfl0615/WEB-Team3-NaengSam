package com.naengsam.quick.domain.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.delivery.dto.GeoPoint;
import com.naengsam.quick.domain.delivery.service.MatchingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 드리미 등록(in) / 제거(out) 엔드포인트가 요청을 올바르게 파싱해 {@link MatchingService} 로
 * 위임하는지 검증한다. 매칭 엔진 자체의 상태 전이는 {@link MatchingService} 단위 테스트에서 다룬다.
 */
class DeliveryControllerTest {

    private MatchingService matchingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(matchingService)).build();
    }

    @Test
    void 드리미_등록시_생성된_ID와_요청한_위치로_서비스에_위임한다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/delivery/dreami")
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

        String firstId = mockMvc.perform(post("/api/v1/delivery/dreami")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String secondId = mockMvc.perform(post("/api/v1/delivery/dreami")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();

        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void 드리미_등록시_본문이_깨져있으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/delivery/dreami")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verify(matchingService, never()).registerDreami(any(), any());
    }

    @Test
    void 드리미_제거시_경로변수의_ID로_서비스에_위임한다() throws Exception {
        UUID dreamiId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/delivery/dreami/{dreamiId}", dreamiId))
                .andExpect(status().isOk());

        verify(matchingService).removeDreami(dreamiId);
    }

    @Test
    void 드리미_제거시_UUID_형식이_아니면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/delivery/dreami/{dreamiId}", "not-a-uuid"))
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

        mockMvc.perform(get("/api/v1/delivery/dreami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dreamiId").value(dreamiId.toString()))
                .andExpect(jsonPath("$[0].location.latitude").value(37.5))
                .andExpect(jsonPath("$[0].status").value("MATCHING"));
    }

    @Test
    void 제거후_조회하면_대기중_드리미_목록에서_사라진다() throws Exception {
        when(matchingService.waitingDreamis()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/delivery/dreami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
