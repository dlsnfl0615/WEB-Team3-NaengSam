package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link Uploader}로의 위임(확장자 검증 포함)을 검증한다. key 발급/용도 검증은 {@link UploadSessionServiceTest}에서 다룬다.
 * 실제 스토리지 호출 로직 자체는 {@link S3UploaderTest}에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class S3PresignServiceTest {

    @Mock
    private Uploader uploader;

    @InjectMocks
    private S3PresignService s3PresignService;

    @Test
    void 업로드_URL_발급시_확장자로_추론한_contentType을_uploader에_그대로_전달한다() {
        when(uploader.generateUploadUrl("uploads/x/y-a.png", "image/png")).thenReturn("https://example.com/upload");

        String url = s3PresignService.generateUploadUrl("uploads/x/y-a.png");

        assertThat(url).isEqualTo("https://example.com/upload");
    }

    @Test
    void 지원하지_않는_확장자면_uploader를_호출하지_않고_예외를_던진다() {
        Throwable thrown = catchThrowable(() -> s3PresignService.generateUploadUrl("uploads/x/y-a.exe"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.UNSUPPORTED_FILE_TYPE);
        verify(uploader, never()).generateUploadUrl(any(), any());
    }

    @Test
    void 존재하지_않는_key로_다운로드_URL을_요청하면_예외를_던진다() {
        when(uploader.exists("uploads/x/y-a.png")).thenReturn(false);

        Throwable thrown = catchThrowable(() -> s3PresignService.generateDownloadUrl("uploads/x/y-a.png"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void 존재하는_key면_다운로드_URL을_발급한다() {
        when(uploader.exists("uploads/x/y-a.png")).thenReturn(true);
        when(uploader.generateDownloadUrl("uploads/x/y-a.png")).thenReturn("https://example.com/download");

        String url = s3PresignService.generateDownloadUrl("uploads/x/y-a.png");

        assertThat(url).isEqualTo("https://example.com/download");
    }

    @Test
    void 업로드_확인은_uploader의_exists에_그대로_위임한다() {
        when(uploader.exists("uploads/x/y-a.png")).thenReturn(true);

        boolean result = s3PresignService.isFileUploaded("uploads/x/y-a.png");

        assertThat(result).isTrue();
    }
}
