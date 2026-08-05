package com.naengsam.quick.domain.dreami.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.naengsam.quick.domain.dreami.service.DreamiService;
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
 * 드리미 제안 수락/거절 엔드포인트가 본인에게 온 제안일 때만 서비스에 위임하는지 검증한다. 정확한 HTTP 상태/에러코드는 명세 확정 전이라 이후 커밋에서 다룬다.
 */
class DreamiControllerTest {

    private DreamiService dreamiService;
    private MatchingService matchingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dreamiService = mock(DreamiService.class);
        matchingService = mock(MatchingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DreamiController(dreamiService, matchingService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 본인에게_온_제안이면_드리미_수락_요청을_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/dreami/offers/{offerId}/accept", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService).acceptByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이_아니면_드리미_수락_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/dreami/offers/{offerId}/accept", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService, never()).acceptByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이면_드리미_거절_요청을_서비스에_위임한다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/dreami/offers/{offerId}/reject", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService).rejectByDreami(offerId);
    }

    @Test
    void 본인에게_온_제안이_아니면_드리미_거절_요청을_위임하지_않는다() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        when(matchingService.isDreamiOfferOwner(offerId, dreamiId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/dreami/offers/{offerId}/reject", offerId)
                .sessionAttr(SessionConst.LOGIN_USER, dreamiId));

        verify(matchingService, never()).rejectByDreami(offerId);
    }
}
