package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.payment.service.WalletService;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.SignUpRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.entity.UserCd;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 유저 도메인 핵심 서비스 단위 테스트. 회원가입/로그인/내정보조회/역할전환의 검증 분기와 상태 전이를 목킹 기반으로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private DreamiRepository dreamiRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SmsVerificationService smsVerificationService;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserService userService;

    private static SignUpRequest signUpRequest() {
        return new SignUpRequest("user@test.com", "pass123", "홍길동", "010-1234-5678",
                LocalDate.of(1990, 1, 1));
    }

    private static Boormi activeBoormi() {
        return Boormi.create("user@test.com", PasswordHasher.hash("pass123"), "홍길동", "01012345678",
                LocalDate.of(1990, 1, 1));
    }

    private static BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    // ---------- signup ----------

    @Test
    void 가입_정상이면_정규화된_번호로_저장하고_지갑을_만들고_인증상태를_소비한다() {
        given(boormiRepository.existsByEmail("user@test.com")).willReturn(false);
        given(boormiRepository.existsByPhoneNumber("01012345678")).willReturn(false);
        given(boormiRepository.existsByName("홍길동")).willReturn(false);
        given(smsVerificationService.isVerified("01012345678")).willReturn(true);

        UserDto result = userService.signup(signUpRequest());

        ArgumentCaptor<Boormi> captor = ArgumentCaptor.forClass(Boormi.class);
        verify(boormiRepository).save(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("01012345678");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("pass123");
        assertThat(PasswordHasher.matches("pass123", captor.getValue().getPassword())).isTrue();
        verify(walletService).createWallet(captor.getValue().getBoormiId());
        verify(smsVerificationService).consumeVerified("01012345678");
        assertThat(result.isDreami()).isFalse();
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    void 가입_이메일이_중복이면_ALREADY_REGISTERED_예외() {
        given(boormiRepository.existsByEmail("user@test.com")).willReturn(true);

        Throwable thrown = catchThrowable(() -> userService.signup(signUpRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.ALREADY_REGISTERED);
        verify(boormiRepository, never()).save(any());
    }

    @Test
    void 가입_전화번호가_중복이면_PHONE_ALREADY_REGISTERED_예외() {
        given(boormiRepository.existsByEmail("user@test.com")).willReturn(false);
        given(boormiRepository.existsByPhoneNumber("01012345678")).willReturn(true);

        Throwable thrown = catchThrowable(() -> userService.signup(signUpRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.PHONE_ALREADY_REGISTERED);
        verify(boormiRepository, never()).save(any());
    }

    @Test
    void 가입_닉네임이_중복이면_DUPLICATE_NICKNAME_예외() {
        given(boormiRepository.existsByEmail("user@test.com")).willReturn(false);
        given(boormiRepository.existsByPhoneNumber("01012345678")).willReturn(false);
        given(boormiRepository.existsByName("홍길동")).willReturn(true);

        Throwable thrown = catchThrowable(() -> userService.signup(signUpRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
        verify(boormiRepository, never()).save(any());
    }

    @Test
    void 가입_휴대폰_미인증이면_PHONE_NOT_VERIFIED_예외이고_저장하지_않는다() {
        given(boormiRepository.existsByEmail("user@test.com")).willReturn(false);
        given(boormiRepository.existsByPhoneNumber("01012345678")).willReturn(false);
        given(boormiRepository.existsByName("홍길동")).willReturn(false);
        given(smsVerificationService.isVerified("01012345678")).willReturn(false);

        Throwable thrown = catchThrowable(() -> userService.signup(signUpRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.PHONE_NOT_VERIFIED);
        verify(boormiRepository, never()).save(any());
        verify(walletService, never()).createWallet(any());
    }

    // ---------- login ----------

    @Test
    void 로그인_정상이면_boormiId_를_반환한다() {
        Boormi boormi = activeBoormi();
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.of(boormi));

        UUID result = userService.login(new LoginRequest("user@test.com", "pass123"));

        assertThat(result).isEqualTo(boormi.getBoormiId());
    }

    @Test
    void 로그인_이메일이_없으면_LOGIN_FAILED_예외() {
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(
                () -> userService.login(new LoginRequest("user@test.com", "pass123")));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    void 로그인_비밀번호가_틀리면_LOGIN_FAILED_예외() {
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.of(activeBoormi()));

        Throwable thrown = catchThrowable(
                () -> userService.login(new LoginRequest("user@test.com", "wrong")));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    void 로그인_RESTRICTED_계정이면_SUSPENDED_ACCOUNT_예외() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "userCd", UserCd.RESTRICTED);
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.of(boormi));

        Throwable thrown = catchThrowable(
                () -> userService.login(new LoginRequest("user@test.com", "pass123")));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.SUSPENDED_ACCOUNT);
    }

    @Test
    void 로그인_BANNED_계정이면_SUSPENDED_ACCOUNT_예외() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "userCd", UserCd.BANNED);
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.of(boormi));

        Throwable thrown = catchThrowable(
                () -> userService.login(new LoginRequest("user@test.com", "pass123")));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.SUSPENDED_ACCOUNT);
    }

    @Test
    void 로그인_DELETED_계정이면_WITHDRAWN_ACCOUNT_예외() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "userCd", UserCd.DELETED);
        given(boormiRepository.findByEmail("user@test.com")).willReturn(Optional.of(boormi));

        Throwable thrown = catchThrowable(
                () -> userService.login(new LoginRequest("user@test.com", "pass123")));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    // ---------- getUserInfo ----------

    @Test
    void 내정보_드리미활성화_AND_승인이면_isDreami_true() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "isDreamiActivate", true);
        UUID id = boormi.getBoormiId();
        Dreami dreami = org.mockito.Mockito.mock(Dreami.class);
        given(dreami.getRequestCd()).willReturn(DreamiCd.APPROVED);
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));

        UserDto result = userService.getUserInfo(id);

        assertThat(result.isDreami()).isTrue();
    }

    @Test
    void 내정보_드리미활성화지만_미승인이면_isDreami_false() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "isDreamiActivate", true);
        UUID id = boormi.getBoormiId();
        Dreami dreami = org.mockito.Mockito.mock(Dreami.class);
        given(dreami.getRequestCd()).willReturn(DreamiCd.REVIEWING);
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));

        UserDto result = userService.getUserInfo(id);

        assertThat(result.isDreami()).isFalse();
    }

    @Test
    void 내정보_드리미활성화지만_레코드가_없으면_isDreami_false() {
        Boormi boormi = activeBoormi();
        ReflectionTestUtils.setField(boormi, "isDreamiActivate", true);
        UUID id = boormi.getBoormiId();
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));
        given(dreamiRepository.findById(id)).willReturn(Optional.empty());

        UserDto result = userService.getUserInfo(id);

        assertThat(result.isDreami()).isFalse();
    }

    @Test
    void 내정보_드리미_비활성화면_드리미조회없이_isDreami_false() {
        Boormi boormi = activeBoormi();
        UUID id = boormi.getBoormiId();
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));

        UserDto result = userService.getUserInfo(id);

        assertThat(result.isDreami()).isFalse();
        verify(dreamiRepository, never()).findById(any());
    }

    @Test
    void 내정보_사용자가_없으면_INVALID_SESSION_예외() {
        UUID id = UUID.randomUUID();
        given(boormiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> userService.getUserInfo(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.INVALID_SESSION);
    }

    // ---------- changeRole ----------

    @Test
    void 역할전환_드리미_미등록이면_DREAMI_NOT_REGISTERED_예외() {
        UUID id = UUID.randomUUID();
        given(dreamiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> userService.changeRole(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(UserErrorCode.DREAMI_NOT_REGISTERED);
    }

    @Test
    void 역할전환_드리미가_미승인이면_DREAMI_NOT_APPROVED_예외() {
        UUID id = UUID.randomUUID();
        Dreami dreami = org.mockito.Mockito.mock(Dreami.class);
        given(dreami.getRequestCd()).willReturn(DreamiCd.REVIEWING);
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));

        Throwable thrown = catchThrowable(() -> userService.changeRole(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(UserErrorCode.DREAMI_NOT_APPROVED);
    }

    @Test
    void 역할전환_진행중인_주문이_있으면_CANNOT_CHANGE_ROLE_예외() {
        UUID id = UUID.randomUUID();
        Dreami dreami = org.mockito.Mockito.mock(Dreami.class);
        given(dreami.getRequestCd()).willReturn(DreamiCd.APPROVED);
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(orderRepository.countActiveOrders(id)).willReturn(1L);

        Throwable thrown = catchThrowable(() -> userService.changeRole(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(UserErrorCode.CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER);
    }

    @Test
    void 역할전환_승인됐고_진행중_주문이_없으면_예외없이_통과() {
        UUID id = UUID.randomUUID();
        Dreami dreami = org.mockito.Mockito.mock(Dreami.class);
        given(dreami.getRequestCd()).willReturn(DreamiCd.APPROVED);
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(orderRepository.countActiveOrders(id)).willReturn(0L);

        assertThatCode(() -> userService.changeRole(id)).doesNotThrowAnyException();
    }
}
