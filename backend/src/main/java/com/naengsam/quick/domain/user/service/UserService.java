package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.payment.service.WalletService;
import com.naengsam.quick.domain.user.dto.ActiveContext;
import com.naengsam.quick.domain.user.dto.ActiveRole;
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

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final BoormiRepository boormiRepository;
    private final DreamiRepository dreamiRepository;
    private final SmsVerificationService smsVerificationService;
    private final WalletService walletService;
    private final UserActivityResolver userActivityResolver;

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
     */
    @Transactional(readOnly = true)
    public UUID login(LoginRequest request) {
        Boormi boormi = boormiRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        if (!PasswordHasher.matches(request.password(), boormi.getPassword())) {
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

        Dreami dreami = boormi.isDreamiActivate()
                ? dreamiRepository.findById(boormiId).orElse(null)
                : null;
        boolean approved = dreami != null && dreami.getRequestCd() == DreamiCd.APPROVED;
        // 승인된 드리미일 때만 드리미 평점을 내려준다(미등록·미승인은 null).
        BigDecimal dreamiAvgScore = approved ? dreami.getDreamiAvgScore() : null;

        return UserDto.from(boormi, approved, dreamiAvgScore, userActivityResolver.resolve(boormiId));
    }

    /**
     * 요청한 역할로 전환할 수 있는지 검사한다. 상태를 바꾸지는 않고(현재 모드는 클라이언트가 보관한다) 통과 여부만 판정한다.
     *
     * <p>매칭·배달이 진행 중이면 어느 방향으로도 전환할 수 없다. 지금 맡고 있는 역할과 같은 역할로 바꾸려는 요청은 사실상 무변화이므로 통과시킨다.
     */
    @Transactional(readOnly = true)
    public void changeRole(UUID boormiId, ActiveRole target) {
        // 1. 드리미로 전환할 때만 승인 여부를 본다. 부르미는 모든 계정이 가진 기본 역할이다.
        if (target == ActiveRole.DREAMI) {
            Dreami dreami = dreamiRepository.findById(boormiId)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.DREAMI_NOT_REGISTERED));
            if (dreami.getRequestCd() != DreamiCd.APPROVED) {
                throw new BusinessException(UserErrorCode.DREAMI_NOT_APPROVED);
            }
        }

        // 2. 수행 중인 역할이 있고 그것이 요청한 역할과 다르면 전환 불가.
        ActiveContext activeContext = userActivityResolver.resolve(boormiId);
        if (!activeContext.isActive() || activeContext.role() == target) {
            return;
        }

        // 주문 없이 활성인 경우는 드리미가 오퍼를 기다리는 중뿐이다. 사용자가 오프라인으로 풀 수 있으므로 안내를 구분한다.
        throw new BusinessException(activeContext.orderId() == null
                ? UserErrorCode.CANNOT_CHANGE_ROLE_WHILE_MATCHING
                : UserErrorCode.CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER);
    }
}
