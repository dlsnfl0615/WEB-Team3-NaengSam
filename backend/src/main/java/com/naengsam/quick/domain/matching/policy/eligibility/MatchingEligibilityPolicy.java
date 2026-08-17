package com.naengsam.quick.domain.matching.policy.eligibility;

import com.naengsam.quick.domain.matching.model.MatchingCandidateView;
import java.time.LocalDateTime;

/**
 * 특정 후보(주문-드리미 조합)를 이번 배정 라운드의 후보 목록에 포함할지 판단하는 정책의 계약.
 * <p>구현체는 다음을 지켜야 한다.
 * <ul>
 *     <li>시스템 시각(예: {@code LocalDateTime.now()})을 직접 조회하지 않는다.</li>
 *     <li>판단 기준은 오직 인자로 받은 evaluatedAt이다.</li>
 *     <li>같은 candidate와 evaluatedAt이 주어지면 항상 같은 결과를 반환한다(결정적이어야 한다).</li>
 *     <li>candidate 등 입력 상태를 변경하지 않는다.</li>
 * </ul>
 */
public interface MatchingEligibilityPolicy {

    boolean isEligible(
            MatchingCandidateView candidate,
            LocalDateTime evaluatedAt
    );
}
