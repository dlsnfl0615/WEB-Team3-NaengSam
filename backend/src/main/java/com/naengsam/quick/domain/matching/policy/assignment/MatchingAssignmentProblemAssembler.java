package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 엔진 상태의 {@link OrderOfferGroup}/{@link WaitingDreami} 스냅샷을 {@link MatchingAssignmentProblemFactory}에 넣을
 * 입력({@link MatchingOrderInput}, {@link MatchingDreamiInput}, {@link MatchingCandidate})으로 변환한다.
 * <p>대상은 WAITING 상태(다음 micro-batch 라운드를 기다리는 중)인 주문 그룹과 MATCHING 상태(다른 방에 들어가지
 * 않은)인 드리미뿐이다. previousInteraction은 같은 주문 그룹의 offers() 이력 중 그 드리미에게 나갔던 오퍼를 찾아, 아직 진행 중이지 않은(응답이 끝난) 것 중 가장 최근 것으로
 * 만든다.
 */
@Service
@RequiredArgsConstructor
public class MatchingAssignmentProblemAssembler {

    private static final int MIN_OFFER_COUNT = 1;
    private final GeoDistanceCalculator geoDistanceCalculator;
    private final MatchingAssignmentProblemFactory matchingAssignmentProblemFactory;
    private final MatchingPolicyProperties matchingPolicyProperties;
    private final Clock clock;

    private static Optional<PreviousOfferOutcome> toOutcome(MatchOfferStatus status) {
        return switch (status) {
            case DREAMI_REJECTED -> Optional.of(PreviousOfferOutcome.DREAMI_REJECTED);
            case BOORMI_REJECTED -> Optional.of(PreviousOfferOutcome.BOORMI_REJECTED);
            case DREAMI_EXPIRED -> Optional.of(PreviousOfferOutcome.DREAMI_EXPIRED);
            case BOORMI_EXPIRED -> Optional.of(PreviousOfferOutcome.BOORMI_EXPIRED);
            case WITHDRAWN -> Optional.of(PreviousOfferOutcome.WITHDRAWN);
            case OFFERED, PENDING_BOORMI_CONFIRMATION, MATCHED -> Optional.empty();
        };
    }

    public MatchingAssignmentProblem assemble(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    ) {
        LocalDateTime evaluatedAt = LocalDateTime.now(clock);

        List<OrderOfferGroup> waitingGroups = orderOfferGroups.stream()
                .filter(group -> group.status() == OrderOfferGroupStatus.WAITING)
                .toList();
        List<WaitingDreami> matchingDreamis = waitingDreamis.stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .toList();

        int maxConcurrentOffers = resolveMaxConcurrentOffers(waitingGroups.size(), matchingDreamis.size());
        List<MatchingOrderInput> orders = waitingGroups.stream()
                .map(group -> MatchingOrderInput.from(group, evaluatedAt, maxConcurrentOffers))
                .toList();
        List<MatchingDreamiInput> dreamis = matchingDreamis.stream()
                .map(dreami -> new MatchingDreamiInput(
                        dreami.dreamiId(), dreami.location(), Duration.between(dreami.updatedAt(), evaluatedAt)))
                .toList();

        Map<UUID, Duration> orderWaitingTimes = orders.stream()
                .collect(Collectors.toMap(MatchingOrderInput::orderId, MatchingOrderInput::waitingTime));
        Map<UUID, Duration> dreamiWaitingTimes = dreamis.stream()
                .collect(Collectors.toMap(MatchingDreamiInput::dreamiId, MatchingDreamiInput::waitingTime));

        List<MatchingCandidate> rawCandidates = new ArrayList<>();
        for (OrderOfferGroup group : waitingGroups) {
            for (WaitingDreami dreami : matchingDreamis) {
                double distanceMeters = geoDistanceCalculator.distanceMeters(group.location(), dreami.location());

                rawCandidates.add(new MatchingCandidate(
                        group.orderId(),
                        dreami.dreamiId(),
                        distanceMeters,
                        orderWaitingTimes.get(group.orderId()),
                        dreamiWaitingTimes.get(dreami.dreamiId()),
                        0,
                        0,
                        findPreviousInteraction(group, dreami.dreamiId())));
            }
        }

        return matchingAssignmentProblemFactory.create(evaluatedAt, orders, dreamis, rawCandidates);
    }

    private Optional<PreviousOfferInteraction> findPreviousInteraction(OrderOfferGroup group, UUID dreamiId) {
        return group.offers().stream()
                .filter(offer -> offer.dreamiId().equals(dreamiId))
                .flatMap(offer -> toOutcome(offer.status())
                        .map(outcome -> new PreviousOfferInteraction(outcome, offer.statusUpdatedAt()))
                        .stream())
                .max(Comparator.comparing(PreviousOfferInteraction::occurredAt));
    }

    /**
     * 이번 배치의 모든 주문에 동일하게 적용할 maxConcurrentOffers를 정책에 따라 계산한다. FIXED는 설정값을 그대로 쓰고, DYNAMIC은 배치 시점의 드리미/주문 비율로 다시 계산한다.
     */
    private int resolveMaxConcurrentOffers(int orderCount, int dreamiCount) {
        return switch (matchingPolicyProperties.offerQuotaMode()) {
            case FIXED -> matchingPolicyProperties.maxConcurrentOffers();
            case DYNAMIC -> calculateDynamicQuota(orderCount, dreamiCount);
        };
    }

    /**
     * 대기 드리미를 대기 주문 수만큼 나눠, 주문 하나가 받을 수 있는 오퍼 수를 올림 계산한다. 주문이 없으면(0으로 나누기 방지) 1을 반환하고, 결과는 선착순 경쟁이 과열되지 않도록
     * [1, {@code matching.dynamic-quota-max}] 범위로 자른다.
     */
    private int calculateDynamicQuota(int orderCount, int dreamiCount) {
        if (orderCount == 0) {
            return MIN_OFFER_COUNT;
        }

        int quota = Math.ceilDiv(dreamiCount, orderCount);
        return Math.clamp(quota, MIN_OFFER_COUNT, matchingPolicyProperties.dynamicQuotaMax());
    }
}
