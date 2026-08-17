package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MatchingPlan이 불변 컬렉션을 보장하고, 빈 제안 목록을 허용하며, 같은 주문에 대한 복수 제안은 허용하되 같은 드리미의
 * 복수 배정·null ID·null 원소는 거부하는지 확인한다.
 */
class MatchingPlanTest {

    @Test
    void 빈_제안_목록도_허용된다() {
        MatchingPlan plan = new MatchingPlan(List.of());

        assertThat(plan.proposals()).isEmpty();
    }

    @Test
    void proposals_목록은_불변이다() {
        List<MatchingProposal> mutableProposals = new ArrayList<>();
        mutableProposals.add(proposal(UUID.randomUUID(), UUID.randomUUID()));
        MatchingPlan plan = new MatchingPlan(mutableProposals);

        Throwable thrown =
                catchThrowable(() -> plan.proposals().add(proposal(UUID.randomUUID(), UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 원본_목록을_수정해도_plan_내부_목록은_바뀌지_않는다() {
        List<MatchingProposal> mutableProposals = new ArrayList<>();
        mutableProposals.add(proposal(UUID.randomUUID(), UUID.randomUUID()));
        MatchingPlan plan = new MatchingPlan(mutableProposals);

        mutableProposals.add(proposal(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(plan.proposals()).hasSize(1);
    }

    @Test
    void 같은_주문에_서로_다른_드리미_여러_명은_허용된다() {
        UUID orderId = UUID.randomUUID();

        MatchingPlan plan = new MatchingPlan(Arrays.asList(
                proposal(orderId, UUID.randomUUID()),
                proposal(orderId, UUID.randomUUID()),
                proposal(orderId, UUID.randomUUID())));

        assertThat(plan.proposals()).hasSize(3);
    }

    @Test
    void 같은_드리미가_여러_주문에_배정되면_예외가_발생한다() {
        UUID dreamiId = UUID.randomUUID();
        List<MatchingProposal> proposals = Arrays.asList(
                proposal(UUID.randomUUID(), dreamiId),
                proposal(UUID.randomUUID(), dreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingPlan(proposals));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_orderId와_dreamiId_조합이_중복되면_예외가_발생한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        List<MatchingProposal> proposals = Arrays.asList(
                proposal(orderId, dreamiId),
                proposal(orderId, dreamiId));

        Throwable thrown = catchThrowable(() -> new MatchingPlan(proposals));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposals가_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(() -> new MatchingPlan(null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_원소가_있으면_예외가_발생한다() {
        List<MatchingProposal> proposals = Arrays.asList(proposal(UUID.randomUUID(), UUID.randomUUID()), null);

        Throwable thrown = catchThrowable(() -> new MatchingPlan(proposals));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderId가_null인_제안이_있으면_예외가_발생한다() {
        List<MatchingProposal> proposals = List.of(proposal(null, UUID.randomUUID()));

        Throwable thrown = catchThrowable(() -> new MatchingPlan(proposals));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamiId가_null인_제안이_있으면_예외가_발생한다() {
        List<MatchingProposal> proposals = List.of(proposal(UUID.randomUUID(), null));

        Throwable thrown = catchThrowable(() -> new MatchingPlan(proposals));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private MatchingProposal proposal(UUID orderId, UUID dreamiId) {
        return new MatchingProposal(orderId, dreamiId, snapshot());
    }

    private OfferPolicySnapshot snapshot() {
        return new OfferPolicySnapshot(Duration.ZERO, LocalDateTime.of(2026, 8, 9, 9, 0), 0L, 0.0, 3_000);
    }
}
