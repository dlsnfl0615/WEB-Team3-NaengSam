package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정이 드리미로 활동할 수 있는 상태인지 판정한다. 활동 가능 조건은 두 가지가 모두 참인 것이다 — 계정이 드리미를 활성화했고, 드리미 심사가 승인됐다.
 *
 * <p>식별자만 알고 있는 호출부(배달 시작 검증)를 위한 것이다. 이미 {@code Boormi} 엔티티를 들고 있는 {@code UserService.getUserInfo}는 같은 규칙을 인라인으로
 * 계산한다 — 여기를 부르면 BOORMI를 한 번 더 조회하게 된다.
 *
 * <p>리포지토리에만 의존하는 잎 컴포넌트로 둔다. 서비스를 참조하면 매칭↔배달↔유저 사이에 순환 참조가 생긴다.
 */
@Component // Spring이 관리하는 일반 빈으로 등록(@Service와 비슷하지만 특정 계층을 의미하지 않음)
@RequiredArgsConstructor // Lombok: final 필드 생성자 자동 생성
public class DreamiActivationChecker {

    private final BoormiRepository boormiRepository;
    private final DreamiRepository dreamiRepository;

    @Transactional(readOnly = true)
    public boolean isActivatedDreami(UUID userId) {
        // Optional.map(람다).orElse(기본값): 값이 있으면 람다로 변환한 결과를, 없으면 기본값(false)을 사용한다.
        // 즉 "boormi가 존재하고 dreamiActivate 플래그가 true인가"를 null 체크 없이 표현한 것.
        boolean activated = boormiRepository.findById(userId)
                .map(boormi -> boormi.isDreamiActivate())
                .orElse(false);
        if (!activated) {
            return false;
        }
        return dreamiRepository.findById(userId)
                .map(dreami -> dreami.getRequestCd() == DreamiCd.APPROVED)
                .orElse(false);
    }
}
