package com.naengsam.quick.domain.upload.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import com.naengsam.quick.global.session.SessionConst;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * presigned URL 발급 시 fileName 검증(첨부 없음/부적절한 이름)이 정의서(FILE_001/FILE_006)대로 동작하는지 검증한다.
 */
class UploadControllerTest {

    private S3PresignService s3PresignService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        s3PresignService = mock(S3PresignService.class);
        DreamiService dreamiService = mock(DreamiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(s3PresignService, dreamiService))
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void fileName이_비어있으면_FILE_001로_거부한다() throws Exception {
        UUID boormiId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/upload/url")
                        .param("fileName", "")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_001"));

        verify(s3PresignService, never()).buildKey(any(), any());
    }

    @Test
    void fileName에_경로_구분자가_있으면_FILE_006으로_거부한다() throws Exception {
        UUID boormiId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/upload/url")
                        .param("fileName", "../secret.png")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_006"));

        verify(s3PresignService, never()).buildKey(any(), any());
    }

    @Test
    void 정상적인_fileName이면_boormiId로_key를_발급받는다() throws Exception {
        UUID boormiId = UUID.randomUUID();
        String key = "uploads/" + boormiId + "/aaa-idcard.png";
        when(s3PresignService.buildKey(boormiId, "idcard.png")).thenReturn(key);
        when(s3PresignService.generateUploadUrl(key)).thenReturn("https://example.com/upload");

        mockMvc.perform(get("/api/v1/upload/url")
                        .param("fileName", "idcard.png")
                        .sessionAttr(SessionConst.LOGIN_USER, boormiId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.url").value("https://example.com/upload"));

        verify(s3PresignService).buildKey(eq(boormiId), eq("idcard.png"));
    }
}
