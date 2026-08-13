package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.payment.service.WalletService;
import com.naengsam.quick.domain.user.dto.ActiveRole;
import com.naengsam.quick.domain.user.dto.LoginCredential;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.SignUpRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.entity.UserCd;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final BoormiRepository boormiRepository;
    private final DreamiRepository dreamiRepository;
    private final OrderRepository orderRepository;
    private final SmsVerificationService smsVerificationService;
    private final WalletService walletService;

    /**
     * 휴대폰 인증을 마친 사용자를 가입시킨다. 이메일/휴대폰 중복과 인증 완료 여부를 검증한 뒤 저장하고, 같은 트랜잭션에서 지갑까지 만든다.
     */
    @Transactional
    public UserDto signup(SignUpRequest request) {
        String phone = PhoneNumbers.normalize(request.phoneNumber());

        if (boormiRepository.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.ALREADY_REGISTERED);
        }
        if (boormiRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException(AuthErrorCode.PHONE_ALREADY_REGISTERED);
        }
        if (boormiRepository.existsByName(request.name())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        if (!smsVerificationService.isVerified(phone)) {
            throw new BusinessException(AuthErrorCode.PHONE_NOT_VERIFIED);
        }

        Boormi boormi = Boormi.create(request.email(), PasswordHasher.hash(request.password()),
                request.name(), phone, request.birthdate());
        boormiRepository.save(boormi);
        walletService.createWallet(boormi.getBoormiId());

        smsVerificationService.consumeVerified(phone);
        return UserDto.from(boormi, false);
    }

    /**
     * 이메일/비밀번호를 검증하고 로그인에 성공하면 사용자 식별자를 반환한다. 반환된 식별자는 컨트롤러가 세션에 저장한다.
     *
     * <p>조회({@link #loadCredential})와 검증({@link #verify})을 나눈 채로 호출한다. 한 트랜잭션으로 묶으면
     * {@code spring.jpa.open-in-view=false} 때문에 210,000회 해싱이 도는 내내 Hikari 커넥션 하나를 잡고 있어,
     * 동시 로그인이 풀 크기(기본 10)를 넘는 순간 11번째부터 커넥션 타임아웃 500이 난다.
     */
    public UUID login(LoginRequest request) {
        return verify(loadCredential(request.email()), request.password());
    }

    /**
     * 로그인 검증에 필요한 값만 조회한다. 해싱 전에 커넥션을 반납하기 위해 조회만 하고 즉시 빠져나온다.
     * 계정이 없어도 여기서 실패하므로 해싱 슬롯을 소모하지 않는다.
     */
    @Transactional(readOnly = true)
    public LoginCredential loadCredential(String email) {
        Boormi boormi = boormiRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        return new LoginCredential(boormi.getBoormiId(), boormi.getPassword(), boormi.getUserCd());
    }

    /**
     * 비밀번호를 검증하고 계정 상태를 확인한다. 210,000회 PBKDF2 해싱이 도는 지점이며 <b>트랜잭션 밖</b>이다.
     * DB 를 건드리지 않으므로 {@code @Transactional} 을 붙이지 않는다.
     */
    public UUID verify(LoginCredential credential, String rawPassword) {
        if (!PasswordHasher.matches(rawPassword, credential.passwordHash())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        if (credential.userCd().equals(UserCd.RESTRICTED) || credential.userCd().equals(UserCd.BANNED)) {
            throw new BusinessException(AuthErrorCode.SUSPENDED_ACCOUNT);
        }

        if (credential.userCd().equals(UserCd.DELETED)) {
            throw new BusinessException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }

        return credential.boormiId();
    }

    /**
     * 세션에서 얻은 식별자로 현재 로그인 사용자 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public UserDto getUserInfo(UUID boormiId) {
        Boormi boormi = boormiRepository.findById(boormiId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SESSION));

        boolean flag = false;
        if (boormi.isDreamiActivate()) {
            flag = dreamiRepository.findById(boormiId)
                    .map(d -> d.getRequestCd() == DreamiCd.APPROVED)
                    .orElse(false);
        }

        Orders activeDreamiOrder = orderRepository.findByDreamiIdAndOrderCd(boormiId, OrderCd.IN_PROGRESS)
                .orElse(null);
        if (activeDreamiOrder != null) {
            return UserDto.from(boormi, flag, ActiveRole.DREAMI, activeDreamiOrder.getOrderId());
        }

        ActiveRole activeRole = orderRepository.countActiveOrders(boormiId) > 0 ? ActiveRole.BOORMI : null;
        return UserDto.from(boormi, flag, activeRole, null);
    }

    public void changeRole(UUID boormiId) {
        // 1. 드리미 승인 여부 확인
        Dreami dreami = dreamiRepository.findById(boormiId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.DREAMI_NOT_REGISTERED));
        if (dreami.getRequestCd() != DreamiCd.APPROVED) {
            throw new BusinessException(UserErrorCode.DREAMI_NOT_APPROVED);
        }

        // 2. 부르미/드리미로서 수행 중인 주문이 있으면 전환 불가
        if (orderRepository.countActiveOrders(boormiId) > 0) {
            throw new BusinessException(UserErrorCode.CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER);
        }
    }
}
