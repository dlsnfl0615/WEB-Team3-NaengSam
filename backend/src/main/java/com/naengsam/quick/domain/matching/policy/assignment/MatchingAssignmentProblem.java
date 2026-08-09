package com.naengsam.quick.domain.matching.policy.assignment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 배정 알고리즘에 입력되는 문제 정의. 특정 시점에 매칭 대상인 주문·드리미 정보의 묶음이며, 빈 목록도 허용한다.
 */
public record MatchingAssignmentProblem(List<MatchingOrderInput> orders, List<MatchingDreamiInput> dreamis) {
    public MatchingAssignmentProblem {
        if (orders == null) {
            throw new IllegalArgumentException("orders는 null일 수 없습니다.");
        }
        if (dreamis == null) {
            throw new IllegalArgumentException("dreamis는 null일 수 없습니다.");
        }
        orders = List.copyOf(orders);
        dreamis = List.copyOf(dreamis);
        requireUniqueIds(orders.stream().map(MatchingOrderInput::orderId).collect(Collectors.toList()), "orderId");
        requireUniqueIds(dreamis.stream().map(MatchingDreamiInput::dreamiId).collect(Collectors.toList()), "dreamiId");
    }

    private static void requireUniqueIds(List<UUID> ids, String fieldName) {
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException(fieldName + "가 중복되었습니다: " + id);
            }
        }
    }
}
