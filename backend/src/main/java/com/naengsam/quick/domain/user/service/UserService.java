package com.naengsam.quick.domain.user.service;

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

        // TODO: 외부 해싱 라이브러리 없이 우선 평문 저장. 후속으로 SHA-256(MessageDigest) 등 해싱 도입 필요.
        Boormi boormi = Boormi.create(request.email(), request.password(), request.name(), phone,
                request.birthdate());
        boormiRepository.save(boormi);
        walletService.createWallet(boormi.getBoormiId());

        smsVerificationService.consumeVerified(phone);
        return UserDto.from(boormi, false);
    }

    /**
     * 이메일/비밀번호를 검증하고 로그인에 성공하면 사용자 식별자를 반환한다. 반환된 식별자는 컨트롤러가 세션에 저장한다.
     */
    @Transactional(readOnly = true)
    public UUID login(LoginRequest request) {
        Boormi boormi = boormiRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        // TODO: 외부 해싱 라이브러리 없이 우선 평문 비교. 후속으로 SHA-256(MessageDigest) 등 해싱 도입 필요.
        if (!boormi.getPassword().equals(request.password())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        if (boormi.getUserCd().equals(UserCd.RESTRICTED) || boormi.getUserCd().equals(UserCd.BANNED)) {
            throw new BusinessException(AuthErrorCode.SUSPENDED_ACCOUNT);
        }

        if (boormi.getUserCd().equals(UserCd.DELETED)) {
            throw new BusinessException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }

        return boormi.getBoormiId();
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

        return UserDto.from(boormi, flag);
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
