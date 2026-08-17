package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblem;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlan;

/** 계획 검증에 필요한 문제와 실제 적용할 계획을 묶은 결과. */
public record MatchingPlanningResult(
        MatchingAssignmentProblem validationProblem,
        MatchingPlan plan
) {
    public MatchingPlanningResult {
        if (validationProblem == null || plan == null) {
            throw new IllegalArgumentException("validationProblem과 plan은 null일 수 없습니다.");
        }
    }
}
