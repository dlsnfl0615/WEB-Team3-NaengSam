package com.naengsam.quick.domain.matching.policy.eligibility;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import java.time.LocalDateTime;

/**
 * legacy {@code MatchOffer.shouldExcludeFromRematch()}의 제외 규칙을 그대로 재현하는 적격성 정책.
 * <p>드리미가 명시적으로 거절했거나(DREAMI_REJECTED) 응답 timeout(DREAMI_EXPIRED)이었거나 부르미가
 * 거절했던(BOORMI_REJECTED) 조합은 다시 후보로 삼지 않는다. 타의로 회수됐거나(WITHDRAWN) 부르미 응답
 * timeout(BOORMI_EXPIRED)인 경우는 드리미 본인의 잘못이 아니므로 다시 후보로 허용한다. 이전 이력이 없으면
 * 당연히 허용한다.
 * <p>evaluatedAt은 시각을 실제로 사용하지 않지만, {@link MatchingEligibilityPolicy} 계약에 맞추기 위해
 * 받기만 한다.
 */
public class LegacyOfferEligibilityPolicy implements MatchingEligibilityPolicy {

    @Override
    public boolean isEligible(
            MatchingCandidate candidate,
            LocalDateTime evaluatedAt
    ) {
        return candidate.previousInteraction()
                .map(PreviousOfferInteraction::outcome)
                .map(outcome -> switch (outcome) {
                    case DREAMI_REJECTED, BOORMI_REJECTED, DREAMI_EXPIRED -> false;
                    case WITHDRAWN, BOORMI_EXPIRED -> true;
                })
                .orElse(true);
    }
}
