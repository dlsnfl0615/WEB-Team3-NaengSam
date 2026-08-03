package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * 실제 S3 호출(존재 확인 시 404/그 외 오류 구분, presigned URL 발급) 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class S3UploaderTest {

    @Mock
    private UploadProperties uploadProperties;

    @Mock
    private S3Presigner presigner;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3Uploader s3Uploader;

    @Test
    void 존재하는_key면_true를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);

        boolean result = s3Uploader.exists("uploads/x/y-a.png");

        assertThat(result).isTrue();
    }

    @Test
    void 아직_업로드되지_않은_key면_예외없이_false를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        boolean result = s3Uploader.exists("uploads/x/y-a.png");

        assertThat(result).isFalse();
    }

    @Test
    void S3_오류가_404가_아니면_예외를_던진다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        Throwable thrown = catchThrowable(() -> s3Uploader.exists("uploads/x/y-a.png"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.STORAGE_UPLOAD_FAILED);
    }

    @Test
    void 다운로드_URL을_발급한다() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/download").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String url = s3Uploader.generateDownloadUrl("uploads/x/y-a.png");

        assertThat(url).isEqualTo("https://example.com/download");
    }
}
