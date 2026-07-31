package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
}
