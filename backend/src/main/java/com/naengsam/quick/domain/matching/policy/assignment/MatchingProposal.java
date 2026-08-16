package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import java.util.UUID;

/**
 * 배정 알고리즘이 해당 라운드에 생성할 오퍼 후보 하나. 최종 배달 배정이 아니라 "이 주문에 이 드리미로 제안을 뿌리겠다"는
 * 제안 후보이며, 실제 확정은 이후 오퍼 절차(수락/거절)에서 결정된다. 같은 orderId가 여러 MatchingProposal에 걸쳐
 * 나타날 수 있다(한 주문에 여러 드리미를 동시에 제안하는 선착순 방식).
 * <p>offerPolicySnapshot은 이 제안이 만들어질 때 적용됐던 offer scope 판단 근거를 그대로 담는다.
 */
public record MatchingProposal(
        UUID orderId,
        UUID dreamiId,
        OfferPolicySnapshot offerPolicySnapshot
) {
    public MatchingProposal {
        if (offerPolicySnapshot == null) {
            throw new IllegalArgumentException("offerPolicySnapshot은 null일 수 없습니다.");
        }
    }
}
