package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.entity.UserCd;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final BoormiRepository boormiRepository;
    private final DreamiRepository dreamiRepository;

    /**
     * 이메일/비밀번호를 검증하고 로그인에 성공하면 사용자 식별자를 반환한다. 반환된 식별자는 컨트롤러가 세션에 저장한다.
     */
    @Transactional(readOnly = true)
    public UUID login(LoginRequest request) {
        Boormi boormi = boormiRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_FAILED));

        // TODO: 외부 해싱 라이브러리 없이 우선 평문 비교. 후속으로 SHA-256(MessageDigest) 등 해싱 도입 필요.
        if (!boormi.getPassword().equals(request.password())) {
            throw new BusinessException(UserErrorCode.LOGIN_FAILED);
        }

        if (boormi.getUserCd().equals(UserCd.RESTRICTED) || boormi.getUserCd().equals(UserCd.BANNED)) {
            throw new BusinessException(UserErrorCode.SUSPENDED_ACCOUNT);
        }

        if (boormi.getUserCd().equals(UserCd.DELETED)) {
            throw new BusinessException(UserErrorCode.WITHDRAWN_ACCOUNT);
        }

        return boormi.getBoormiId();
    }

    /**
     * 세션에서 얻은 식별자로 현재 로그인 사용자 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public UserDto getUserInfo(UUID boormiId) {
        Boormi boormi = boormiRepository.findById(boormiId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_SESSION));

        boolean flag = false;
        if (boormi.isDreamiActivate()) {
            Dreami dreami = dreamiRepository.findById(boormiId).orElse(null);
            if (dreami.getRequestCd().equals(DreamiCd.APPROVED)) {
                flag = true;
            }
        }

        return UserDto.from(boormi, flag);
    }
}
