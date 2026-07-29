package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.service.VerificationCodeStore.VerifyResult;
import com.naengsam.quick.domain.user.sms.SmsSender;
import com.naengsam.quick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 문자 인증 오케스트레이션 단위 테스트. 저장소/문자발송기를 목킹하고 쿨다운·검증 결과 분기와 전화번호 정규화 위임을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SmsVerificationServiceTest {

    @Mock
    private VerificationCodeStore store;

    @Mock
    private SmsSendRateLimiter rateLimiter;

    @Mock
    private SmsSender smsSender;

    @InjectMocks
    private SmsVerificationService smsVerificationService;

    @Test
    void 쿨다운이_아니고_한도_이내면_인증번호를_발급해_정규화된_번호로_문자를_보낸다() {
        given(store.canResend("01012345678")).willReturn(true);
        given(rateLimiter.tryAcquire("01012345678")).willReturn(true);
        given(store.issue("01012345678")).willReturn("135790");

        smsVerificationService.send("010-1234-5678");

        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(phoneCaptor.capture(), textCaptor.capture());
        assertThat(phoneCaptor.getValue()).isEqualTo("01012345678");
        assertThat(textCaptor.getValue()).contains("135790");
    }

    @Test
    void 쿨다운_이내_재요청이면_예외를_던지고_문자를_보내지_않는다() {
        given(store.canResend("01012345678")).willReturn(false);

        Throwable thrown = catchThrowable(() -> smsVerificationService.send("010-1234-5678"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_REQUEST_EXCEEDED);
        verify(store, never()).issue(anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void 발송_한도를_초과하면_예외를_던지고_문자를_보내지_않는다() {
        given(store.canResend("01012345678")).willReturn(true);
        given(rateLimiter.tryAcquire("01012345678")).willReturn(false);

        Throwable thrown = catchThrowable(() -> smsVerificationService.send("010-1234-5678"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_REQUEST_EXCEEDED);
        verify(store, never()).issue(anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void 검증_결과가_EXPIRED_이면_만료_예외를_던진다() {
        given(store.verify("01012345678", "123456")).willReturn(VerifyResult.EXPIRED);

        Throwable thrown = catchThrowable(() -> smsVerificationService.verify("010-1234-5678", "123456"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }

    @Test
    void 검증_결과가_MISMATCH_이면_잘못된_인증번호_예외를_던진다() {
        given(store.verify("01012345678", "123456")).willReturn(VerifyResult.MISMATCH);

        Throwable thrown = catchThrowable(() -> smsVerificationService.verify("010-1234-5678", "123456"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_VERIFICATION_CODE);
    }

    @Test
    void 검증_결과가_TOO_MANY_ATTEMPTS_이면_시도_초과_예외를_던진다() {
        given(store.verify("01012345678", "123456")).willReturn(VerifyResult.TOO_MANY_ATTEMPTS);

        Throwable thrown = catchThrowable(() -> smsVerificationService.verify("010-1234-5678", "123456"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_ATTEMPTS_EXCEEDED);
    }

    @Test
    void 검증_결과가_OK_이면_예외를_던지지_않는다() {
        given(store.verify("01012345678", "123456")).willReturn(VerifyResult.OK);

        assertThatCode(() -> smsVerificationService.verify("010-1234-5678", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void isVerified_는_정규화된_번호로_저장소에_위임한다() {
        given(store.isVerified("01011112222")).willReturn(true);

        boolean result = smsVerificationService.isVerified("010-1111-2222");

        assertThat(result).isTrue();
        verify(store).isVerified("01011112222");
    }

    @Test
    void consumeVerified_는_정규화된_번호로_저장소에_위임한다() {
        smsVerificationService.consumeVerified("010-1111-2222");

        verify(store).consumeVerified("01011112222");
    }
}
