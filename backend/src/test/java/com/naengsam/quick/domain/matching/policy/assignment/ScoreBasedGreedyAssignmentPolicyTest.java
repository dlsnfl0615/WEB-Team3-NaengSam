package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.policy.scoring.DistanceOnlyScorePolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * ScoreBasedGreedyAssignmentPolicy가 orders 입력 순서가 아니라 scorePolicy가 매긴 전역 점수순으로
 * 배정하는지, 그리고 배정 제약(드리미 전역 유일성, 주문별 maxConcurrentOffers)을 지키는지 확인한다.
 */
class ScoreBasedGreedyAssignmentPolicyTest {

    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    private final OfferScopeResolver offerScopeResolver = new OfferScopeResolver(
            List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));
    private final ScoreBasedGreedyAssignmentPolicy policy =
            new ScoreBasedGreedyAssignmentPolicy(new DistanceOnlyScorePolicy(), offerScopeResolver);

    @Test
    void 입력_순서상_뒤에_있는_주문도_점수가_더_좋으면_먼저_배정받는다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID dreamiForA = UUID.randomUUID();
        UUID dreamiForB = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 1), orderInput(orderB, 1)),
                List.of(dreamiInput(dreamiForA), dreamiInput(dreamiForB)),
                List.of(
                        candidate(orderA, dreamiForA, 500L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, dreamiForB, 10L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).containsExactlyInAnyOrder(
                new MatchingProposal(orderA, dreamiForA, snapshot(500L, Duration.ZERO)),
                new MatchingProposal(orderB, dreamiForB, snapshot(10L, Duration.ZERO)));
    }

    @Test
    void 같은_드리미를_두_주문이_원하면_점수가_더_좋은_주문이_가져간다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID sharedDreami = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 1), orderInput(orderB, 1)),
                List.of(dreamiInput(sharedDreami)),
                List.of(
                        candidate(orderA, sharedDreami, 900L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, sharedDreami, 100L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).containsExactly(
                new MatchingProposal(orderB, sharedDreami, snapshot(100L, Duration.ZERO)));
    }

    @Test
    void 주문별_maxConcurrentOffers를_넘지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderId, 2)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2), dreamiInput(dreami3)),
                List.of(
                        candidate(orderId, dreami1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami2, 20L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami3, 30L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).hasSize(2);
        assertThat(proposedDreamiIds(plan)).containsExactly(dreami1, dreami2);
    }

    @Test
    void 한_드리미는_전역에서_하나의_주문에만_배정된다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID sharedDreami = UUID.randomUUID();
        UUID onlyForB = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 1), orderInput(orderB, 1)),
                List.of(dreamiInput(sharedDreami), dreamiInput(onlyForB)),
                List.of(
                        candidate(orderA, sharedDreami, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, sharedDreami, 20L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, onlyForB, 30L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        List<MatchingProposal> sharedDreamiProposals = plan.proposals().stream()
                .filter(proposal -> proposal.dreamiId().equals(sharedDreami))
                .toList();
        assertThat(sharedDreamiProposals).hasSize(1);
        assertThat(sharedDreamiProposals.get(0).orderId()).isEqualTo(orderA);
        assertThat(proposedDreamiIds(plan)).containsExactlyInAnyOrder(sharedDreami, onlyForB);
    }

    @Test
    void 점수가_같으면_주문_대기시간이_긴_쪽이_우선한다() {
        UUID orderShortWait = UUID.randomUUID();
        UUID orderLongWait = UUID.randomUUID();
        UUID sharedDreami = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderShortWait, 1), orderInput(orderLongWait, 1)),
                List.of(dreamiInput(sharedDreami)),
                List.of(
                        candidate(orderShortWait, sharedDreami, 100L, Duration.ofMinutes(1), Duration.ZERO),
                        candidate(orderLongWait, sharedDreami, 100L, Duration.ofMinutes(10), Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).containsExactly(
                new MatchingProposal(orderLongWait, sharedDreami, snapshot(100L, Duration.ofMinutes(10))));
    }

    @Test
    void 후보가_없는_주문은_제안이_생성되지_않는다() {
        UUID orderId = UUID.randomUUID();
        MatchingAssignmentProblem problem =
                new MatchingAssignmentProblem(EVALUATED_AT, List.of(orderInput(orderId, 3)), List.of(), List.of());

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).isEmpty();
    }

    @Test
    void 빈_주문과_빈_드리미_배치는_빈_계획을_반환한다() {
        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT, List.of(), List.of(), List.of());

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).isEmpty();
    }

    @Test
    void 같은_문제에_두_번_호출해도_같은_결과가_나온다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderId, 1)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2)),
                List.of(
                        candidate(orderId, dreami1, 50L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami2, 60L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan first = policy.createPlan(problem);
        MatchingPlan second = policy.createPlan(problem);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void 전역_점수_순서로_배정하되_모든_주문의_capacity와_드리미_독점_제약을_지킨다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();
        UUID dreami4 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 2), orderInput(orderB, 1)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2), dreamiInput(dreami3), dreamiInput(dreami4)),
                List.of(
                        candidate(orderB, dreami1, 5L, Duration.ZERO, Duration.ZERO),
                        candidate(orderA, dreami1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderA, dreami2, 20L, Duration.ZERO, Duration.ZERO),
                        candidate(orderA, dreami3, 30L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, dreami4, 40L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        // 전역 정렬: (B,d1,5) -> (A,d1,10, d1 이미 소진돼 스킵) -> (A,d2,20) -> (A,d3,30) -> (B,d4,40, B는 이미 capacity 도달해 스킵)
        assertThat(plan.proposals()).containsExactlyInAnyOrder(
                new MatchingProposal(orderB, dreami1, snapshot(5L, Duration.ZERO)),
                new MatchingProposal(orderA, dreami2, snapshot(20L, Duration.ZERO)),
                new MatchingProposal(orderA, dreami3, snapshot(30L, Duration.ZERO)));
        assertThat(countByOrder(plan, orderA)).isEqualTo(2);
        assertThat(countByOrder(plan, orderB)).isEqualTo(1);
        assertThat(proposedDreamiIds(plan)).doesNotHaveDuplicates();
    }

    @Test
    void 여러_주문이_있어도_각자_capacity를_준수한다() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID dreamiA1 = UUID.randomUUID();
        UUID dreamiA2 = UUID.randomUUID();
        UUID dreamiA3 = UUID.randomUUID();
        UUID dreamiB1 = UUID.randomUUID();
        UUID dreamiB2 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderA, 2), orderInput(orderB, 1)),
                List.of(dreamiInput(dreamiA1), dreamiInput(dreamiA2), dreamiInput(dreamiA3),
                        dreamiInput(dreamiB1), dreamiInput(dreamiB2)),
                List.of(
                        candidate(orderA, dreamiA1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderA, dreamiA2, 20L, Duration.ZERO, Duration.ZERO),
                        candidate(orderA, dreamiA3, 30L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, dreamiB1, 40L, Duration.ZERO, Duration.ZERO),
                        candidate(orderB, dreamiB2, 50L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(countByOrder(plan, orderA)).isEqualTo(2);
        assertThat(countByOrder(plan, orderB)).isEqualTo(1);
    }

    @Test
    void 일부_주문만_후보가_있으면_후보_있는_주문만_배정받는다() {
        UUID orderWithCandidates = UUID.randomUUID();
        UUID orderWithoutCandidates = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderWithCandidates, 1), orderInput(orderWithoutCandidates, 1)),
                List.of(dreamiInput(dreamiId)),
                List.of(candidate(orderWithCandidates, dreamiId, 10L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).containsExactly(
                new MatchingProposal(orderWithCandidates, dreamiId, snapshot(10L, Duration.ZERO)));
        assertThat(countByOrder(plan, orderWithoutCandidates)).isZero();
    }

    @Test
    void 같은_주문에_서로_다른_드리미가_capacity까지_배정된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();
        UUID dreami3 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderId, 3)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2), dreamiInput(dreami3)),
                List.of(
                        candidate(orderId, dreami1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami2, 20L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami3, 30L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(proposedDreamiIds(plan)).containsExactlyInAnyOrder(dreami1, dreami2, dreami3);
        assertThat(proposedDreamiIds(plan)).doesNotHaveDuplicates();
    }

    @Test
    void 동일_드리미가_같은_주문에_중복으로_배정되지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderId, 5)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2)),
                List.of(
                        candidate(orderId, dreami1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami2, 20L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(proposedDreamiIds(plan)).doesNotHaveDuplicates();
    }

    @Test
    void capacity가_3인_주문에_후보가_2명이면_2건만_생성된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreami1 = UUID.randomUUID();
        UUID dreami2 = UUID.randomUUID();

        MatchingAssignmentProblem problem = new MatchingAssignmentProblem(EVALUATED_AT,
                List.of(orderInput(orderId, 3)),
                List.of(dreamiInput(dreami1), dreamiInput(dreami2)),
                List.of(
                        candidate(orderId, dreami1, 10L, Duration.ZERO, Duration.ZERO),
                        candidate(orderId, dreami2, 20L, Duration.ZERO, Duration.ZERO)));

        MatchingPlan plan = policy.createPlan(problem);

        assertThat(plan.proposals()).hasSize(2);
        assertThat(proposedDreamiIds(plan)).containsExactlyInAnyOrder(dreami1, dreami2);
    }

    private long countByOrder(MatchingPlan plan, UUID orderId) {
        return plan.proposals().stream().filter(proposal -> proposal.orderId().equals(orderId)).count();
    }

    private List<UUID> proposedDreamiIds(MatchingPlan plan) {
        return plan.proposals().stream().map(MatchingProposal::dreamiId).collect(Collectors.toList());
    }

    private MatchingOrderInput orderInput(UUID orderId, int maxConcurrentOffers) {
        return new MatchingOrderInput(orderId, LOCATION, Duration.ZERO, maxConcurrentOffers);
    }

    private MatchingDreamiInput dreamiInput(UUID dreamiId) {
        return new MatchingDreamiInput(dreamiId, LOCATION, Duration.ZERO);
    }

    private MatchingCandidate candidate(UUID orderId, UUID dreamiId, long distanceMeters,
            Duration orderWaitingTime, Duration dreamiWaitingTime) {
        return new MatchingCandidate(
                orderId, dreamiId, distanceMeters, orderWaitingTime, dreamiWaitingTime, 0, 0, Optional.empty());
    }

    private OfferPolicySnapshot snapshot(long distanceMeters, Duration orderWaitingTime) {
        return new OfferPolicySnapshot(
                Duration.ZERO, EVALUATED_AT, orderWaitingTime.toSeconds(), distanceMeters, 3_000);
    }
}
