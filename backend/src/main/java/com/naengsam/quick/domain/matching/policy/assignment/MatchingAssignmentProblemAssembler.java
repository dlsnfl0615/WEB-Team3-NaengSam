package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.planning.MatchingPlanningSnapshot;
import com.naengsam.quick.domain.matching.policy.planning.MatchingPlanningSnapshotFactory;
import com.naengsam.quick.domain.matching.policy.scope.OfferScope;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 엔진 상태의 {@link OrderOfferGroup}/{@link WaitingDreami} 스냅샷을 {@link MatchingAssignmentProblemFactory}에 넣을
 * 입력({@link MatchingOrderInput}, {@link MatchingDreamiInput}, {@link MatchingCandidate})으로 변환한다.
 * <p>대상은 WAITING 상태(다음 micro-batch 라운드를 기다리는 중)인 주문 그룹과 MATCHING 상태(다른 방에 들어가지
 * 않은)인 드리미뿐이다. previousInteraction은 같은 주문 그룹의 offers() 이력 중 그 드리미에게 나갔던 오퍼를 찾아, 아직 진행 중이지 않은(응답이 끝난) 것 중 가장 최근 것으로
 * 만든다.
 * <p>주문 대기시간으로 고른 {@link OfferScope}의 maxPickupDistanceMeters를 넘는 (주문, 드리미) 조합은 raw
 * candidate 자체를 만들지 않는다 - 그 주문의 모든 드리미가 이 필터에 걸리면 해당 주문은 이번 라운드에 candidate가
 * 하나도 없는 채로 문제에 남고, 배정 정책은 그 주문에 대해 자연히 빈 제안만 만든다.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MatchingAssignmentProblemAssembler {

    private final GeoDistanceCalculator geoDistanceCalculator;
    private final MatchingAssignmentProblemFactory matchingAssignmentProblemFactory;
    private final MatchingPlanningSnapshotFactory snapshotFactory;
    private final MeterRegistry meterRegistry;

    public MatchingAssignmentProblemAssembler(
            GeoDistanceCalculator geoDistanceCalculator,
            MatchingAssignmentProblemFactory matchingAssignmentProblemFactory,
            MatchingPolicyProperties matchingPolicyProperties,
            Clock clock,
            OfferScopeResolver offerScopeResolver,
            MeterRegistry meterRegistry
    ) {
        this(
                geoDistanceCalculator,
                matchingAssignmentProblemFactory,
                new MatchingPlanningSnapshotFactory(matchingPolicyProperties, clock, offerScopeResolver),
                meterRegistry);
    }

    public MatchingAssignmentProblem assemble(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    ) {
        MatchingPlanningSnapshot snapshot = snapshotFactory.create(orderOfferGroups, waitingDreamis);

        List<MatchingCandidate> rawCandidates = new ArrayList<>();
        for (int orderIndex = 0; orderIndex < snapshot.orders().size(); orderIndex++) {
            MatchingOrderInput order = snapshot.orders().get(orderIndex);
            OfferScope offerScope = snapshot.offerScopes().get(orderIndex);

            for (int dreamiIndex = 0; dreamiIndex < snapshot.dreamis().size(); dreamiIndex++) {
                MatchingDreamiInput dreami = snapshot.dreamis().get(dreamiIndex);
                double distanceMeters = geoDistanceCalculator.distanceMeters(order.location(), dreami.location());

                if (distanceMeters > offerScope.maxPickupDistanceMeters()) {
                    meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                            .increment();
                    continue;
                }

                rawCandidates.add(new MatchingCandidate(
                        order.orderId(),
                        dreami.dreamiId(),
                        distanceMeters,
                        order.waitingTime(),
                        dreami.waitingTime(),
                        0,
                        0,
                        Optional.ofNullable(snapshot.previousInteractionsByOrder().get(orderIndex)
                                .get(dreami.dreamiId()))));
            }
        }

        return matchingAssignmentProblemFactory.create(
                snapshot.evaluatedAt(), snapshot.orders(), snapshot.dreamis(), rawCandidates);
    }
}
