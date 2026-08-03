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
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
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
 * key의 용도/소유자 스코프 검증과, 재시도 시 중복 저장되지 않는지(세션 소비 멱등성)를 검증한다.
 */
class DreamiAuthControllerTest {

    private S3PresignService s3PresignService;
    private UploadSessionService uploadSessionService;
    private DreamiService dreamiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        s3PresignService = mock(S3PresignService.class);
        uploadSessionService = mock(UploadSessionService.class);
        dreamiService = mock(DreamiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DreamiAuthController(s3PresignService, uploadSessionService, dreamiService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 본인이_해당_용도로_발급받은_key로_확인하면_업로드_여부를_검사하고_인증신청을_저장한다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        when(s3PresignService.isFileUploaded(idCardKey)).thenReturn(true);
        when(s3PresignService.isFileUploaded(criminalRecordKey)).thenReturn(true);
        when(uploadSessionService.consume(idCardKey)).thenReturn(true);
        when(uploadSessionService.consume(criminalRecordKey)).thenReturn(true);

        mockMvc.perform(post("/api/v1/dreami/check")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(uploadSessionService).validateScope(UploadPurpose.DREAMI_ID_CARD, boormiId, null, idCardKey);
        verify(uploadSessionService).validateScope(UploadPurpose.DREAMI_CRIMINAL_RECORD, boormiId, null,
                criminalRecordKey);
        verify(dreamiService).saveVerificationFileKeys(boormiId, idCardKey, criminalRecordKey);
    }

    @Test
    void 다른_용도_다른_사람에게_발급된_key를_제출하면_403을_반환하고_저장하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String stolenKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        doThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .when(uploadSessionService).validateScope(UploadPurpose.DREAMI_ID_CARD, boormiId, null, stolenKey);

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

    @Test
    void 아직_업로드가_안됐으면_저장하지_않고_false를_반환한다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        when(s3PresignService.isFileUploaded(idCardKey)).thenReturn(false);

        mockMvc.perform(post("/api/v1/dreami/check")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        verify(uploadSessionService, never()).consume(any());
        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }

    @Test
    void 재시도로_이미_소비된_세션이면_저장을_반복하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        when(s3PresignService.isFileUploaded(idCardKey)).thenReturn(true);
        when(s3PresignService.isFileUploaded(criminalRecordKey)).thenReturn(true);
        when(uploadSessionService.consume(idCardKey)).thenReturn(false);
        when(uploadSessionService.consume(criminalRecordKey)).thenReturn(false);

        mockMvc.perform(post("/api/v1/dreami/check")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }
}
