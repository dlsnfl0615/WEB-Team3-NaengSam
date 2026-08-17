package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.MatchingCandidateView;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblem;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingDreamiInput;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingOrderInput;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlan;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingProposal;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.eligibility.MatchingEligibilityPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import com.naengsam.quick.domain.matching.policy.scoring.MatchingScorePolicy;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 후보를 {@code orderIndex * dreamiCount + dreamiIndex}로 표현해 객체 그래프 없이 정확히 정렬·배정한다.
 * <p>이 정책은 엔진의 단일 스레드에서만 호출된다는 계약으로 grow-only 배열과 cursor를 재사용한다. 동시에 호출하면
 * 내부 버퍼가 공유되므로 안전하지 않다. cursor는 정책 호출 중에만 유효하며 점수·적격성 정책은 이를 보관할 수 없다.
 */
public class PrimitiveIndexMatchingPlanningPolicy implements MatchingPlanningPolicy {

    private static final int INITIAL_CAPACITY = 16;

    private final MatchingPlanningSnapshotFactory snapshotFactory;
    private final GeoDistanceCalculator geoDistanceCalculator;
    private final MatchingEligibilityPolicy eligibilityPolicy;
    private final MatchingScorePolicy scorePolicy;
    private final AssignmentPolicyType assignmentPolicyType;
    private final MeterRegistry meterRegistry;
    private final ReusableCandidateCursor cursor = new ReusableCandidateCursor();

    private double[] distances = new double[0];
    private long[] scores = new long[0];
    private long[] eligibilityWords = new long[0];
    private int[] ordinals = new int[0];
    private int[] mergeScratch = new int[0];
    private int[] orderCandidateCounts = new int[0];
    private int[] dreamiCandidateCounts = new int[0];
    private int[] assignedCounts = new int[0];
    private boolean[] consumedDreamis = new boolean[0];

    public PrimitiveIndexMatchingPlanningPolicy(
            MatchingPlanningSnapshotFactory snapshotFactory,
            GeoDistanceCalculator geoDistanceCalculator,
            MatchingEligibilityPolicy eligibilityPolicy,
            MatchingScorePolicy scorePolicy,
            MatchingPolicyProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.snapshotFactory = snapshotFactory;
        this.geoDistanceCalculator = geoDistanceCalculator;
        this.eligibilityPolicy = eligibilityPolicy;
        this.scorePolicy = scorePolicy;
        this.assignmentPolicyType = properties.assignmentPolicy();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public MatchingPlanningResult createPlan(
            List<OrderOfferGroup> orderOfferGroups,
            List<WaitingDreami> waitingDreamis
    ) {
        MatchingPlanningSnapshot snapshot = snapshotFactory.create(orderOfferGroups, waitingDreamis);
        int orderCount = snapshot.orders().size();
        int dreamiCount = snapshot.dreamis().size();
        long matrixSize = (long) orderCount * dreamiCount;
        if (matrixSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "매칭 후보 행렬이 int 인덱스 한도를 초과했습니다: orderCount=" + orderCount
                            + ", dreamiCount=" + dreamiCount + ", matrixSize=" + matrixSize);
        }

        int candidateCapacity = (int) matrixSize;
        ensureCapacity(candidateCapacity, orderCount, dreamiCount);
        clearRoundState(candidateCapacity, orderCount, dreamiCount);
        cursor.use(snapshot);
        try {
            int eligibleCount = evaluateEligibility(snapshot, orderCount, dreamiCount);
            scoreEligibleCandidates(snapshot, candidateCapacity, dreamiCount);
            sortEligibleOrdinals(snapshot, eligibleCount, dreamiCount);
            return select(snapshot, eligibleCount, dreamiCount);
        } finally {
            cursor.clear();
        }
    }

    private int evaluateEligibility(MatchingPlanningSnapshot snapshot, int orderCount, int dreamiCount) {
        int eligibleCount = 0;
        for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
            MatchingOrderInput order = snapshot.orders().get(orderIndex);
            long maxDistance = snapshot.offerScopes().get(orderIndex).maxPickupDistanceMeters();
            for (int dreamiIndex = 0; dreamiIndex < dreamiCount; dreamiIndex++) {
                MatchingDreamiInput dreami = snapshot.dreamis().get(dreamiIndex);
                int flatIndex = orderIndex * dreamiCount + dreamiIndex;
                double distance = geoDistanceCalculator.distanceMeters(order.location(), dreami.location());
                distances[flatIndex] = distance;

                if (distance > maxDistance) {
                    meterRegistry.counter("matching.candidates.filtered", "reason", "pickup_distance_exceeded")
                            .increment();
                    continue;
                }

                cursor.move(orderIndex, dreamiIndex, distance, 0, 0);
                if (!eligibilityPolicy.isEligible(cursor, snapshot.evaluatedAt())) {
                    continue;
                }

                setEligible(flatIndex);
                orderCandidateCounts[orderIndex]++;
                dreamiCandidateCounts[dreamiIndex]++;
                eligibleCount++;
            }
        }
        return eligibleCount;
    }

    private void scoreEligibleCandidates(
            MatchingPlanningSnapshot snapshot,
            int candidateCapacity,
            int dreamiCount
    ) {
        int ordinalIndex = 0;
        for (int flatIndex = 0; flatIndex < candidateCapacity; flatIndex++) {
            if (!isEligible(flatIndex)) {
                continue;
            }
            int orderIndex = flatIndex / dreamiCount;
            int dreamiIndex = flatIndex % dreamiCount;
            cursor.move(orderIndex, dreamiIndex, distances[flatIndex],
                    orderCandidateCounts[orderIndex], dreamiCandidateCounts[dreamiIndex]);
            scores[flatIndex] = scorePolicy.score(cursor);
            ordinals[ordinalIndex++] = flatIndex;
        }
    }

    private void sortEligibleOrdinals(MatchingPlanningSnapshot snapshot, int eligibleCount, int dreamiCount) {
        if (eligibleCount < 2) {
            return;
        }

        int[] source = ordinals;
        int[] target = mergeScratch;
        int width = 1;
        while (width < eligibleCount) {
            int start = 0;
            while (start < eligibleCount) {
                int middle = (int) Math.min((long) start + width, eligibleCount);
                int end = (int) Math.min((long) start + width * 2L, eligibleCount);
                merge(source, target, start, middle, end, snapshot, dreamiCount);
                start = end;
            }
            int[] swap = source;
            source = target;
            target = swap;
            if (width > eligibleCount / 2) {
                break;
            }
            width *= 2;
        }

        if (source != ordinals) {
            System.arraycopy(source, 0, ordinals, 0, eligibleCount);
        }
    }

    private void merge(
            int[] source,
            int[] target,
            int start,
            int middle,
            int end,
            MatchingPlanningSnapshot snapshot,
            int dreamiCount
    ) {
        int left = start;
        int right = middle;
        for (int targetIndex = start; targetIndex < end; targetIndex++) {
            if (left < middle && (right >= end
                    || compare(source[left], source[right], snapshot, dreamiCount) <= 0)) {
                target[targetIndex] = source[left++];
            } else {
                target[targetIndex] = source[right++];
            }
        }
    }

    private int compare(int leftFlatIndex, int rightFlatIndex, MatchingPlanningSnapshot snapshot, int dreamiCount) {
        int leftOrderIndex = leftFlatIndex / dreamiCount;
        int rightOrderIndex = rightFlatIndex / dreamiCount;
        int leftDreamiIndex = leftFlatIndex % dreamiCount;
        int rightDreamiIndex = rightFlatIndex % dreamiCount;

        if (assignmentPolicyType == AssignmentPolicyType.LEGACY_ORDER_FIRST) {
            int orderInputOrder = Integer.compare(leftOrderIndex, rightOrderIndex);
            if (orderInputOrder != 0) {
                return orderInputOrder;
            }
        }

        int scoreOrder = Long.compare(scores[leftFlatIndex], scores[rightFlatIndex]);
        if (scoreOrder != 0) {
            return scoreOrder;
        }

        if (assignmentPolicyType == AssignmentPolicyType.SCORE_BASED_GREEDY) {
            int orderWaitOrder = snapshot.orders().get(rightOrderIndex).waitingTime()
                    .compareTo(snapshot.orders().get(leftOrderIndex).waitingTime());
            if (orderWaitOrder != 0) {
                return orderWaitOrder;
            }
        }

        int dreamiWaitOrder = snapshot.dreamis().get(rightDreamiIndex).waitingTime()
                .compareTo(snapshot.dreamis().get(leftDreamiIndex).waitingTime());
        if (dreamiWaitOrder != 0) {
            return dreamiWaitOrder;
        }

        if (assignmentPolicyType == AssignmentPolicyType.SCORE_BASED_GREEDY) {
            int orderIdOrder = snapshot.orders().get(leftOrderIndex).orderId()
                    .compareTo(snapshot.orders().get(rightOrderIndex).orderId());
            if (orderIdOrder != 0) {
                return orderIdOrder;
            }
        }

        return snapshot.dreamis().get(leftDreamiIndex).dreamiId()
                .compareTo(snapshot.dreamis().get(rightDreamiIndex).dreamiId());
    }

    private MatchingPlanningResult select(
            MatchingPlanningSnapshot snapshot,
            int eligibleCount,
            int dreamiCount
    ) {
        List<MatchingCandidate> selectedCandidates = new ArrayList<>();
        List<MatchingProposal> proposals = new ArrayList<>();

        for (int ordinalIndex = 0; ordinalIndex < eligibleCount; ordinalIndex++) {
            int flatIndex = ordinals[ordinalIndex];
            int orderIndex = flatIndex / dreamiCount;
            int dreamiIndex = flatIndex % dreamiCount;
            MatchingOrderInput order = snapshot.orders().get(orderIndex);
            MatchingDreamiInput dreami = snapshot.dreamis().get(dreamiIndex);

            if (consumedDreamis[dreamiIndex] || assignedCounts[orderIndex] >= order.maxConcurrentOffers()) {
                continue;
            }

            Optional<PreviousOfferInteraction> previousInteraction = Optional.ofNullable(
                    snapshot.previousInteractionsByOrder().get(orderIndex).get(dreami.dreamiId()));
            MatchingCandidate candidate = new MatchingCandidate(
                    order.orderId(),
                    dreami.dreamiId(),
                    distances[flatIndex],
                    order.waitingTime(),
                    dreami.waitingTime(),
                    orderCandidateCounts[orderIndex],
                    dreamiCandidateCounts[dreamiIndex],
                    previousInteraction);
            OfferPolicySnapshot offerPolicySnapshot = new OfferPolicySnapshot(
                    snapshot.offerScopeKeys().get(orderIndex),
                    snapshot.evaluatedAt(),
                    order.waitingTime().toSeconds(),
                    distances[flatIndex],
                    snapshot.offerScopes().get(orderIndex).maxPickupDistanceMeters());

            selectedCandidates.add(candidate);
            proposals.add(new MatchingProposal(order.orderId(), dreami.dreamiId(), offerPolicySnapshot));
            consumedDreamis[dreamiIndex] = true;
            assignedCounts[orderIndex]++;
        }

        MatchingAssignmentProblem validationProblem = new MatchingAssignmentProblem(
                snapshot.evaluatedAt(), snapshot.orders(), snapshot.dreamis(), selectedCandidates);
        return new MatchingPlanningResult(validationProblem, new MatchingPlan(proposals));
    }

    private void ensureCapacity(int candidateCapacity, int orderCount, int dreamiCount) {
        if (distances.length < candidateCapacity) {
            int newCapacity = growCapacity(distances.length, candidateCapacity);
            distances = new double[newCapacity];
            scores = new long[newCapacity];
            ordinals = new int[newCapacity];
            mergeScratch = new int[newCapacity];
        }
        int wordCount = Math.ceilDiv(candidateCapacity, Long.SIZE);
        if (eligibilityWords.length < wordCount) {
            eligibilityWords = new long[growCapacity(eligibilityWords.length, wordCount)];
        }
        if (orderCandidateCounts.length < orderCount) {
            int newCapacity = growCapacity(orderCandidateCounts.length, orderCount);
            orderCandidateCounts = new int[newCapacity];
            assignedCounts = new int[newCapacity];
        }
        if (dreamiCandidateCounts.length < dreamiCount) {
            int newCapacity = growCapacity(dreamiCandidateCounts.length, dreamiCount);
            dreamiCandidateCounts = new int[newCapacity];
            consumedDreamis = new boolean[newCapacity];
        }
    }

    private void clearRoundState(int candidateCapacity, int orderCount, int dreamiCount) {
        Arrays.fill(eligibilityWords, 0, Math.ceilDiv(candidateCapacity, Long.SIZE), 0L);
        Arrays.fill(orderCandidateCounts, 0, orderCount, 0);
        Arrays.fill(dreamiCandidateCounts, 0, dreamiCount, 0);
        Arrays.fill(assignedCounts, 0, orderCount, 0);
        Arrays.fill(consumedDreamis, 0, dreamiCount, false);
    }

    private int growCapacity(int currentCapacity, int requiredCapacity) {
        int capacity = Math.max(currentCapacity, INITIAL_CAPACITY);
        while (capacity < requiredCapacity) {
            if (capacity > Integer.MAX_VALUE / 2) {
                return requiredCapacity;
            }
            capacity *= 2;
        }
        return capacity;
    }

    private void setEligible(int flatIndex) {
        eligibilityWords[flatIndex / Long.SIZE] |= 1L << (flatIndex % Long.SIZE);
    }

    private boolean isEligible(int flatIndex) {
        return (eligibilityWords[flatIndex / Long.SIZE] & (1L << (flatIndex % Long.SIZE))) != 0;
    }

    private static final class ReusableCandidateCursor implements MatchingCandidateView {

        private MatchingPlanningSnapshot snapshot;
        private int orderIndex;
        private int dreamiIndex;
        private double distanceMeters;
        private int orderCandidateCount;
        private int dreamiCandidateCount;

        private void use(MatchingPlanningSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void clear() {
            this.snapshot = null;
        }

        private void move(
                int orderIndex,
                int dreamiIndex,
                double distanceMeters,
                int orderCandidateCount,
                int dreamiCandidateCount
        ) {
            this.orderIndex = orderIndex;
            this.dreamiIndex = dreamiIndex;
            this.distanceMeters = distanceMeters;
            this.orderCandidateCount = orderCandidateCount;
            this.dreamiCandidateCount = dreamiCandidateCount;
        }

        @Override
        public UUID orderId() {
            return snapshot.orders().get(orderIndex).orderId();
        }

        @Override
        public UUID dreamiId() {
            return snapshot.dreamis().get(dreamiIndex).dreamiId();
        }

        @Override
        public double distanceMeters() {
            return distanceMeters;
        }

        @Override
        public Duration orderWaitingTime() {
            return snapshot.orders().get(orderIndex).waitingTime();
        }

        @Override
        public Duration dreamiWaitingTime() {
            return snapshot.dreamis().get(dreamiIndex).waitingTime();
        }

        @Override
        public int orderCandidateCount() {
            return orderCandidateCount;
        }

        @Override
        public int dreamiCandidateCount() {
            return dreamiCandidateCount;
        }

        @Override
        public Optional<PreviousOfferInteraction> previousInteraction() {
            return Optional.ofNullable(
                    snapshot.previousInteractionsByOrder().get(orderIndex).get(dreamiId()));
        }
    }
}
