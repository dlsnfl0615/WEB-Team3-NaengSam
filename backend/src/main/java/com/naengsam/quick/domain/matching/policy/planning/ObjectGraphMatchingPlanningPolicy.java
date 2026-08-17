package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblem;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlan;
import java.util.List;

/** 기존 객체 후보 조립 후 배정하는 흐름을 planning policy 경계로 감싼다. */
public class ObjectGraphMatchingPlanningPolicy implements MatchingPlanningPolicy {

    private final MatchingAssignmentProblemAssembler problemAssembler;
    private final MatchingAssignmentPolicy assignmentPolicy;

    public ObjectGraphMatchingPlanningPolicy(
            MatchingAssignmentProblemAssembler problemAssembler,
            MatchingAssignmentPolicy assignmentPolicy
    ) {
        this.problemAssembler = problemAssembler;
        this.assignmentPolicy = assignmentPolicy;
    }

    @Override
    public MatchingPlanningResult createPlan(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    ) {
        MatchingAssignmentProblem problem = problemAssembler.assemble(orderOfferGroups, waitingDreamis);
        MatchingPlan plan = assignmentPolicy.createPlan(problem);
        return new MatchingPlanningResult(problem, plan);
    }
}
