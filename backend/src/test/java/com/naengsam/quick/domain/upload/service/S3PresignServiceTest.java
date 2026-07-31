package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.net.URI;
import java.util.UUID;
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
 * key와 발급 요청자(boormiId)의 바인딩 - key 발급/소유자 검증 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class S3PresignServiceTest {

    @Mock
    private UploadProperties uploadProperties;

    @Mock
    private S3Presigner presigner;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3PresignService s3PresignService;

    @Test
    void key를_발급하면_boormiId_경로_아래에_생성된다() {
        UUID boormiId = UUID.randomUUID();

        String key = s3PresignService.buildKey(boormiId, "idcard.png");

        assertThat(key).startsWith("uploads/" + boormiId + "/");
        assertThat(key).endsWith("-idcard.png");
    }

    @Test
    void 같은_boormiId여도_호출할때마다_다른_key가_생성된다() {
        UUID boormiId = UUID.randomUUID();

        String first = s3PresignService.buildKey(boormiId, "idcard.png");
        String second = s3PresignService.buildKey(boormiId, "idcard.png");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 발급받은_본인이_확인하면_예외없이_통과한다() {
        UUID boormiId = UUID.randomUUID();
        String key = s3PresignService.buildKey(boormiId, "idcard.png");

        assertThatCode(() -> s3PresignService.validateOwnership(boormiId, key))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_사람에게_발급된_key를_제출하면_예외를_던진다() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String key = s3PresignService.buildKey(owner, "idcard.png");

        Throwable thrown = catchThrowable(() -> s3PresignService.validateOwnership(attacker, key));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.KEY_OWNER_MISMATCH);
    }

    @Test
    void 존재하는_key면_true를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);

        boolean result = s3PresignService.isFileUploaded("uploads/x/y-a.png");

        assertThat(result).isTrue();
    }

    @Test
    void 아직_업로드되지_않은_key면_예외없이_false를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        boolean result = s3PresignService.isFileUploaded("uploads/x/y-a.png");

        assertThat(result).isFalse();
    }

    @Test
    void S3_오류가_404가_아니면_예외를_던진다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        Throwable thrown = catchThrowable(() -> s3PresignService.isFileUploaded("uploads/x/y-a.png"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.STORAGE_UPLOAD_FAILED);
    }

    @Test
    void 존재하지_않는_key로_다운로드_URL을_요청하면_예외를_던진다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        Throwable thrown = catchThrowable(() -> s3PresignService.generateDownloadUrl("uploads/x/y-a.png"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UploadErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void 존재하는_key면_다운로드_URL을_발급한다() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/download").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String url = s3PresignService.generateDownloadUrl("uploads/x/y-a.png");

        assertThat(url).isEqualTo("https://example.com/download");
    }
}
