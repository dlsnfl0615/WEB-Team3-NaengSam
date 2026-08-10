package com.naengsam.quick.domain.dreami.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
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
 * key의 용도/소유자 스코프 검증과, 재시도 시 중복 저장되지 않는지(세션 소비 멱등성 포함)를 검증한다.
 */
class DreamiAuthControllerTest {

    private UploadSessionService uploadSessionService;
    private DreamiService dreamiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadSessionService = mock(UploadSessionService.class);
        dreamiService = mock(DreamiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DreamiAuthController(uploadSessionService, dreamiService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 이미_승인된_드리미면_409를_반환하고_업로드_확인을_시도하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        doThrow(new BusinessException(DreamiErrorCode.ALREADY_APPROVED))
                .when(dreamiService).assertNotAlreadyApproved(boormiId);

        mockMvc.perform(post("/api/v1/dreami/verification")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isConflict());

        verify(uploadSessionService, never()).checkUpload(any(), any(), any(), any());
        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }

    @Test
    void 본인이_해당_용도로_발급받은_key로_확인하면_업로드_여부를_검사하고_인증신청을_저장한다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        when(uploadSessionService.checkUpload(UploadPurpose.DREAMI_ID_CARD, boormiId, null, idCardKey))
                .thenReturn(true);
        when(uploadSessionService.checkUpload(UploadPurpose.DREAMI_CRIMINAL_RECORD, boormiId, null, criminalRecordKey))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/dreami/verification")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk());

        verify(dreamiService).saveVerificationFileKeys(boormiId, idCardKey, criminalRecordKey);
    }

    @Test
    void 다른_용도_다른_사람에게_발급된_key를_제출하면_403을_반환하고_저장하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String stolenKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        doThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .when(uploadSessionService).checkUpload(UploadPurpose.DREAMI_ID_CARD, boormiId, null, stolenKey);

        mockMvc.perform(post("/api/v1/dreami/verification")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(stolenKey, stolenKey)))
                .andExpect(status().isForbidden());

        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }

    @Test
    void 아직_업로드가_안됐으면_FILE_NOT_FOUND_예외이고_저장하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        doThrow(new BusinessException(UploadErrorCode.FILE_NOT_FOUND))
                .when(uploadSessionService).checkUpload(UploadPurpose.DREAMI_ID_CARD, boormiId, null, idCardKey);

        mockMvc.perform(post("/api/v1/dreami/verification")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isNotFound());

        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }

    @Test
    void 재시도로_둘_다_이미_소비된_세션이면_저장을_반복하지_않는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String idCardKey = "uploads/DREAMI_ID_CARD/aaa-idcard.png";
        String criminalRecordKey = "uploads/DREAMI_CRIMINAL_RECORD/bbb-criminal.png";
        when(uploadSessionService.checkUpload(UploadPurpose.DREAMI_ID_CARD, boormiId, null, idCardKey))
                .thenReturn(false);
        when(uploadSessionService.checkUpload(UploadPurpose.DREAMI_CRIMINAL_RECORD, boormiId, null, criminalRecordKey))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/dreami/verification")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCardKey": "%s", "criminalRecordKey": "%s"}
                                """.formatted(idCardKey, criminalRecordKey)))
                .andExpect(status().isOk());

        verify(dreamiService, never()).saveVerificationFileKeys(any(), any(), any());
    }
}
