package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import java.util.List;

/** 엔진 상태 스냅샷에서 검증용 문제와 적용할 매칭 계획을 함께 만든다. */
public interface MatchingPlanningPolicy {

    MatchingPlanningResult createPlan(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    );
}
