package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 배정 알고리즘에 입력되는 문제 정의. 특정 시점에 매칭 대상인 주문·드리미 정보와, 이미 필터링을 거친 허용 후보 조합({@link MatchingCandidate})의 묶음이며 빈 목록도 허용한다.
 * candidates는 이미 필터링된 허용 목록이므로, 배정 알고리즘은 여기 없는 주문-드리미 조합을 결과에 포함할 수 없다. 거절·만료 이력을 읽어 후보에서 제외하는 책임은 이 레코드가 아니라 이 문제를 만드는
 * 쪽(MatchingAssignmentProblemFactory)에 있다.
 */
public record MatchingAssignmentProblem(
        List<MatchingOrderInput> orders,
        List<MatchingDreamiInput> dreamis,
        List<MatchingCandidate> candidates
) {
    public MatchingAssignmentProblem {
        if (orders == null) {
            throw new IllegalArgumentException("orders는 null일 수 없습니다.");
        }
        if (dreamis == null) {
            throw new IllegalArgumentException("dreamis는 null일 수 없습니다.");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates는 null일 수 없습니다.");
        }

        orders = List.copyOf(orders);
        dreamis = List.copyOf(dreamis);

        Set<UUID> orderIds = orders.stream().map(MatchingOrderInput::orderId).collect(Collectors.toSet());
        Set<UUID> dreamiIds = dreamis.stream().map(MatchingDreamiInput::dreamiId).collect(Collectors.toSet());
        requireUniqueIds(orders.stream().map(MatchingOrderInput::orderId).collect(Collectors.toList()), "orderId");
        requireUniqueIds(dreamis.stream().map(MatchingDreamiInput::dreamiId).collect(Collectors.toList()), "dreamiId");

        Set<CandidateKey> seenCandidateKeys = new HashSet<>();
        for (MatchingCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates는 null 원소를 포함할 수 없습니다.");
            }
            if (!orderIds.contains(candidate.orderId())) {
                throw new IllegalArgumentException("candidate의 orderId가 orders에 존재하지 않습니다: " + candidate.orderId());
            }
            if (!dreamiIds.contains(candidate.dreamiId())) {
                throw new IllegalArgumentException("candidate의 dreamiId가 dreamis에 존재하지 않습니다: " + candidate.dreamiId());
            }
            if (!seenCandidateKeys.add(new CandidateKey(candidate.orderId(), candidate.dreamiId()))) {
                throw new IllegalArgumentException(
                        "candidate가 중복되었습니다: orderId=" + candidate.orderId() + ", dreamiId=" + candidate.dreamiId());
            }
        }
        candidates = List.copyOf(candidates);
    }

    private static void requireUniqueIds(List<UUID> ids, String fieldName) {
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException(fieldName + "가 중복되었습니다: " + id);
            }
        }
    }

    private record CandidateKey(UUID orderId, UUID dreamiId) {
    }
}
