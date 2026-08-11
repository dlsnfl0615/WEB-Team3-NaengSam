package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.PreviousOfferOutcome;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.MatchingService;
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
 * 엔진 상태({@link MatchingService}가 들고 있는 {@link OrderOfferGroup}/{@link WaitingDreami})를
 * {@link MatchingAssignmentProblemFactory}에 넣을 입력({@link MatchingOrderInput}, {@link MatchingDreamiInput},
 * {@link MatchingCandidate})으로 변환한다.
 * <p>대상은 WAITING 상태(다음 micro-batch 라운드를 기다리는 중)인 주문 그룹과 MATCHING 상태(다른 방에 들어가지
 * 않은)인 드리미뿐이다. previousInteraction은 같은 주문 그룹의 offers() 이력 중 그 드리미에게 나갔던 오퍼를 찾아,
 * 아직 진행 중이지 않은(응답이 끝난) 것 중 가장 최근 것으로 만든다.
 */
@Service
@RequiredArgsConstructor
public class MatchingAssignmentProblemAssembler {

    private final MatchingService matchingService;
    private final BoormiService boormiService;
    private final MatchingAssignmentProblemFactory matchingAssignmentProblemFactory;
    private final MatchingPolicyProperties matchingPolicyProperties;
    private final Clock clock;

    public MatchingAssignmentProblem assemble() {
        LocalDateTime evaluatedAt = LocalDateTime.now(clock);

        List<OrderOfferGroup> waitingGroups = matchingService.orderOfferGroups().stream()
                .filter(group -> group.status() == OrderOfferGroupStatus.WAITING)
                .toList();
        List<WaitingDreami> matchingDreamis = matchingService.waitingDreamis().stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .toList();

        List<MatchingOrderInput> orders = waitingGroups.stream()
                .map(group -> MatchingOrderInput.from(
                        group, evaluatedAt, matchingPolicyProperties.maxConcurrentOffers()))
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
                double distanceMeters = boormiService.distanceMeters(group.location(), dreami.location());

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
}
