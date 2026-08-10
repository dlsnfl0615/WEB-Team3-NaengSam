package com.naengsam.quick.domain.matching.policy.assignment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 배정 알고리즘이 한 라운드에 생성한 오퍼 후보({@link MatchingProposal}) 묶음. 최종 배달 배정이 아니라 이번 라운드에
 * 뿌릴 오퍼 후보 목록이며, 같은 orderId가 여러 proposal에 걸쳐 나타나는 건 허용한다(한 주문에 여러 드리미를 동시에
 * 제안). 다만 한 드리미는 동시에 한 곳에만 제안받을 수 있으므로 dreamiId는 중복될 수 없다. 주문별 최대 동시 제안 수
 * (maxConcurrentOffers) 초과 여부는 이 레코드만으로 판단할 수 없어 {@link MatchingPlanValidator}가 별도로 검증한다.
 */
public record MatchingPlan(
        List<MatchingProposal> proposals
) {
    public MatchingPlan {
        if (proposals == null) {
            throw new IllegalArgumentException("proposals는 null일 수 없습니다.");
        }

        Set<UUID> seenDreamiIds = new HashSet<>();
        for (MatchingProposal proposal : proposals) {
            if (proposal == null) {
                throw new IllegalArgumentException("proposals는 null 원소를 포함할 수 없습니다.");
            }
            if (proposal.orderId() == null) {
                throw new IllegalArgumentException("proposal의 orderId는 null일 수 없습니다.");
            }
            if (proposal.dreamiId() == null) {
                throw new IllegalArgumentException("proposal의 dreamiId는 null일 수 없습니다.");
            }
            if (!seenDreamiIds.add(proposal.dreamiId())) {
                throw new IllegalArgumentException("dreamiId가 중복되었습니다: " + proposal.dreamiId());
            }
        }
        proposals = List.copyOf(proposals);
    }
}
