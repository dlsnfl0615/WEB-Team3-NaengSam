package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.domain.upload.dto.PresignedUrlResponseDto;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.entity.UploadSession;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.repository.UploadSessionRepository;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 업로드 세션 발급/스코프 검증/소비(재시도 멱등성 포함)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    @Mock
    private S3PresignService s3PresignService;

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    @InjectMocks
    private UploadSessionService uploadSessionService;

    private static BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    // ---------- issue ----------

    @Test
    void 발급하면_purpose가_새겨진_key로_세션을_저장한다() {
        UUID boormiId = UUID.randomUUID();
        given(s3PresignService.generateUploadUrl(any())).willReturn("https://example.com/upload");

        PresignedUrlResponseDto result = uploadSessionService.issue(UploadPurpose.DREAMI_ID_CARD, boormiId, null,
                "idcard.png");

        assertThat(result.key()).startsWith("uploads/DREAMI_ID_CARD/");
        assertThat(result.key()).endsWith("-idcard.png");
        assertThat(result.url()).isEqualTo("https://example.com/upload");

        ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
        verify(uploadSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(UploadPurpose.DREAMI_ID_CARD);
        assertThat(captor.getValue().getBoormiId()).isEqualTo(boormiId);
    }

    // ---------- validateScope ----------

    @Test
    void 발급된_purpose_boormiId_resourceId가_모두_일치하면_예외없이_통과한다() {
        UUID boormiId = UUID.randomUUID();
        UploadSession session = UploadSession.issue(UploadPurpose.DREAMI_ID_CARD, boormiId, null, "uploads/x/y-a.png");
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.of(session));

        assertThatCode(() -> uploadSessionService.validateScope(UploadPurpose.DREAMI_ID_CARD, boormiId, null,
                "uploads/x/y-a.png")).doesNotThrowAnyException();
    }

    @Test
    void 다른_용도로_발급된_key면_KEY_OWNER_MISMATCH_예외() {
        UUID boormiId = UUID.randomUUID();
        UploadSession session = UploadSession.issue(UploadPurpose.DREAMI_ID_CARD, boormiId, null, "uploads/x/y-a.png");
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.of(session));

        Throwable thrown = catchThrowable(() -> uploadSessionService.validateScope(
                UploadPurpose.DREAMI_CRIMINAL_RECORD, boormiId, null, "uploads/x/y-a.png"));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.KEY_OWNER_MISMATCH);
    }

    @Test
    void 다른_사람에게_발급된_key면_KEY_OWNER_MISMATCH_예외() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UploadSession session = UploadSession.issue(UploadPurpose.DREAMI_ID_CARD, owner, null, "uploads/x/y-a.png");
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.of(session));

        Throwable thrown = catchThrowable(() -> uploadSessionService.validateScope(UploadPurpose.DREAMI_ID_CARD,
                attacker, null, "uploads/x/y-a.png"));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.KEY_OWNER_MISMATCH);
    }

    @Test
    void 발급된_적_없는_key면_FILE_NOT_FOUND_예외() {
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> uploadSessionService.validateScope(UploadPurpose.DREAMI_ID_CARD,
                UUID.randomUUID(), null, "uploads/x/y-a.png"));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.FILE_NOT_FOUND);
    }

    // ---------- consume ----------

    @Test
    void 처음_소비하면_true를_반환한다() {
        UploadSession session = UploadSession.issue(UploadPurpose.DREAMI_ID_CARD, UUID.randomUUID(), null,
                "uploads/x/y-a.png");
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.of(session));

        boolean result = uploadSessionService.consume("uploads/x/y-a.png");

        assertThat(result).isTrue();
    }

    @Test
    void 이미_소비된_세션을_다시_소비하면_예외없이_false를_반환한다() {
        UploadSession session = UploadSession.issue(UploadPurpose.DREAMI_ID_CARD, UUID.randomUUID(), null,
                "uploads/x/y-a.png");
        session.consume();
        given(uploadSessionRepository.findByS3Key("uploads/x/y-a.png")).willReturn(Optional.of(session));

        boolean result = uploadSessionService.consume("uploads/x/y-a.png");

        assertThat(result).isFalse();
    }
}
