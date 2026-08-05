package com.naengsam.quick.domain.matching.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import com.naengsam.quick.global.session.SessionConst;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 매칭 제안 수락/거절 컨트롤러가 본인에게 온 제안일 때만 서비스에 위임하는지 검증한다. 정확한 HTTP 상태/에러코드는 명세 확정 전이라 이후 커밋에서 다룬다.
 */
class MatchingControllerTest {

    private MatchingService matchingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        matchingService = mock(MatchingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MatchingController(matchingService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 본인에게_온_제안이면_드리미_수락_요청을_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/dreami-accept", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService).acceptByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이_아니면_드리미_수락_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/dreami-accept", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService, never()).acceptByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이면_드리미_거절_요청을_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/dreami-reject", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService).rejectByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이_아니면_드리미_거절_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/dreami-reject", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService, never()).rejectByDreami(offerId);
    }

    @Test
    void 본인_주문이_아니면_부르미_수락_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        when(matchingService.isBoormiOfferOwner(offerId, boormiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/boormi-accept", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, boormiId));

        verify(matchingService, never()).acceptByBoormi(offerId);
    }
    

    @Test
    void 본인_주문이_아니면_부르미_거절_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        when(matchingService.isBoormiOfferOwner(offerId, boormiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/matching/offers/{offerId}/boormi-reject", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, boormiId));

        verify(matchingService, never()).rejectByBoormi(offerId);
    }
}
