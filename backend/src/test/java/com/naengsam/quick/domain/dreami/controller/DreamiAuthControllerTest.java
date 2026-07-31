package com.naengsam.quick.domain.dreami.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import com.naengsam.quick.global.session.SessionConst;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 다른 사용자에게 발급된 key를 제출했을 때 업로드 확인/저장으로 이어지지 않는지(소유자 검증) 검증한다.
 */
class DreamiAuthControllerTest {

    private S3PresignService s3PresignService;
    private DreamiService dreamiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        s3PresignService = mock(S3PresignService.class);
        dreamiService = mock(DreamiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DreamiAuthController(s3PresignService, dreamiService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 본인이_발급받은_key로_확인하면_업로드_여부를_검사하고_인증신청을_저장한다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/" + boormiId + "/aaa-idcard.png";
        String criminalRecordKey = "uploads/" + boormiId + "/bbb-criminal.png";
        when(s3PresignService.isFileUploaded(idCardKey)).thenReturn(true);
        when(s3PresignService.isFileUploaded(criminalRecordKey)).thenReturn(true);

        mockMvc.perform(post("/api/v1/dreami/check")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(dreamiService).saveVerificationFileKeys(boormiId, idCardKey, criminalRecordKey);
    }

    @Test
    void 다른_사람에게_발급된_key를_제출하면_403을_반환하고_저장하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        UUID otherBoormiId = UUID.randomUUID();
        String stolenKey = "uploads/" + otherBoormiId + "/aaa-idcard.png";
        doThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .when(s3PresignService).validateOwnership(boormiId, stolenKey);

        mockMvc.perform(post("/api/v1/dreami/check")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(stolenKey, stolenKey)))
                .andExpect(status().isForbidden());

        verify(s3PresignService, never()).isFileUploaded(any());
        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }
}
