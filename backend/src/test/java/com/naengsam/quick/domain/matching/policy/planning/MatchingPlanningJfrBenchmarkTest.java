package com.naengsam.quick.domain.matching.policy.planning;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemFactory;
import com.naengsam.quick.domain.matching.policy.assignment.ScoreBasedGreedyAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.config.AssignmentPolicyType;
import com.naengsam.quick.domain.matching.policy.config.EligibilityPolicyType;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.policy.config.OfferQuotaMode;
import com.naengsam.quick.domain.matching.policy.config.PlanningPolicyType;
import com.naengsam.quick.domain.matching.policy.config.ScoringPolicyType;
import com.naengsam.quick.domain.matching.policy.eligibility.LegacyOfferPolicy;
import com.naengsam.quick.domain.matching.policy.scope.OfferScopeResolver;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScorePolicy;
import com.naengsam.quick.domain.matching.policy.scoring.BalancedScoreWeights;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import com.sun.management.ThreadMXBean;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** 500×500 고정 입력에서 object graph와 primitive index의 JFR·할당량을 수동 비교한다. */
class MatchingPlanningJfrBenchmarkTest {

    private static final int SIZE = 500;
    private static final int WARMUP_CYCLES = 3;
    private static final int MEASURE_CYCLES = 3;
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 17, 12, 0);
    private static final Clock CLOCK = Clock.fixed(EVALUATED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final GeoPoint LOCATION = new GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void 고정_500x500_입력의_JFR과_할당량을_기록한다() throws Exception {
        boolean enabled = Boolean.getBoolean("matching.jfr")
                || Boolean.parseBoolean(System.getenv("MATCHING_JFR"));
        Assumptions.assumeTrue(enabled,
                "수동 실행: MATCHING_JFR=true ./gradlew test --tests '*MatchingPlanningJfrBenchmarkTest'");

        MatchingPolicyProperties properties = properties();
        OfferScopeResolver scopeResolver = new OfferScopeResolver(properties.offerScopes());
        LegacyOfferPolicy eligibilityPolicy = new LegacyOfferPolicy();
        BalancedScorePolicy scorePolicy = new BalancedScorePolicy(
                new BalancedScoreWeights(1, 1, 1),
                3_000,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
        GeoDistanceCalculator distanceCalculator = new ConstantDistanceCalculator();
        MatchingPlanningSnapshotFactory snapshotFactory =
                new MatchingPlanningSnapshotFactory(properties, CLOCK, scopeResolver);
        MatchingAssignmentProblemAssembler assembler = new MatchingAssignmentProblemAssembler(
                distanceCalculator,
                new MatchingAssignmentProblemFactory(eligibilityPolicy),
                snapshotFactory,
                new SimpleMeterRegistry());

        MatchingPlanningPolicy objectGraph = new ObjectGraphMatchingPlanningPolicy(
                assembler, new ScoreBasedGreedyAssignmentPolicy(scorePolicy, scopeResolver));
        MatchingPlanningPolicy primitiveIndex = new PrimitiveIndexMatchingPlanningPolicy(
                snapshotFactory,
                distanceCalculator,
                eligibilityPolicy,
                scorePolicy,
                properties,
                new SimpleMeterRegistry());
        Input input = input();

        warmUp(objectGraph, input);
        warmUp(primitiveIndex, input);

        Path reportDirectory = Path.of("build", "reports", "jfr");
        Files.createDirectories(reportDirectory);
        BenchmarkResult objectResult = record(
                "object-graph", objectGraph, input, reportDirectory.resolve("object-graph-500x500.jfr"));
        BenchmarkResult primitiveResult = record(
                "primitive-index", primitiveIndex, input, reportDirectory.resolve("primitive-index-500x500.jfr"));

        System.out.printf(
                "JFR_BENCHMARK policy=%s cycles=%d elapsed_ms=%.3f allocated_bytes=%d allocation_classes=%s file=%s%n",
                objectResult.policy(),
                MEASURE_CYCLES,
                objectResult.elapsedNanos() / 1_000_000.0,
                objectResult.allocatedBytes(),
                objectResult.allocationClasses(),
                objectResult.recordingPath());
        System.out.printf(
                "JFR_BENCHMARK policy=%s cycles=%d elapsed_ms=%.3f allocated_bytes=%d allocation_classes=%s file=%s%n",
                primitiveResult.policy(),
                MEASURE_CYCLES,
                primitiveResult.elapsedNanos() / 1_000_000.0,
                primitiveResult.allocatedBytes(),
                primitiveResult.allocationClasses(),
                primitiveResult.recordingPath());
    }

    private void warmUp(MatchingPlanningPolicy policy, Input input) {
        for (int cycle = 0; cycle < WARMUP_CYCLES; cycle++) {
            policy.createPlan(input.groups(), input.dreamis());
        }
    }

    private BenchmarkResult record(
            String policyName,
            MatchingPlanningPolicy policy,
            Input input,
            Path recordingPath
    ) throws Exception {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().threadId();

        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ZERO);
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ZERO);
            recording.start();
            long allocatedBefore = threadMXBean.getThreadAllocatedBytes(threadId);
            long startedAt = System.nanoTime();

            for (int cycle = 0; cycle < MEASURE_CYCLES; cycle++) {
                policy.createPlan(input.groups(), input.dreamis());
            }

            long elapsedNanos = System.nanoTime() - startedAt;
            long allocatedBytes = threadMXBean.getThreadAllocatedBytes(threadId) - allocatedBefore;
            recording.stop();
            recording.dump(recordingPath);
            return new BenchmarkResult(
                    policyName,
                    elapsedNanos,
                    allocatedBytes,
                    allocationClasses(recordingPath),
                    recordingPath.toAbsolutePath());
        }
    }

    private Map<String, Long> allocationClasses(Path recordingPath) throws Exception {
        Map<String, Long> result = new HashMap<>();
        for (RecordedEvent event : RecordingFile.readAllEvents(recordingPath)) {
            RecordedClass objectClass = event.getClass("objectClass");
            String className = objectClass.getName();
            if (className.contains("MatchingCandidate")
                    || className.contains("CandidateKey")
                    || className.equals("java.util.ArrayList")) {
                result.merge(className, 1L, Long::sum);
            }
        }
        return Map.copyOf(result);
    }

    private Input input() {
        List<OrderOfferGroup> groups = IntStream.range(0, SIZE)
                .mapToObj(index -> new OrderOfferGroup(
                        new UUID(1, index),
                        new UUID(2, index),
                        LOCATION,
                        null,
                        List.of(),
                        EVALUATED_AT.minusMinutes(5)))
                .toList();
        List<WaitingDreami> dreamis = IntStream.range(0, SIZE)
                .mapToObj(index -> new WaitingDreami(
                        new UUID(3, index),
                        LOCATION,
                        WaitingDreamiStatus.MATCHING,
                        EVALUATED_AT.minusMinutes(5)))
                .toList();
        return new Input(groups, dreamis);
    }

    private MatchingPolicyProperties properties() {
        return new MatchingPolicyProperties(
                Duration.ofSeconds(1),
                3,
                OfferQuotaMode.FIXED,
                5,
                PlanningPolicyType.PRIMITIVE_INDEX,
                AssignmentPolicyType.SCORE_BASED_GREEDY,
                ScoringPolicyType.BALANCED,
                EligibilityPolicyType.LEGACY,
                new MatchingPolicyProperties.Cooldown(
                        Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new MatchingPolicyProperties.BalancedWeights(
                        1, 1, 1, 3_000, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                List.of(new MatchingPolicyProperties.OfferScopeThreshold(Duration.ZERO, 3_000)));
    }

    private static final class ConstantDistanceCalculator extends GeoDistanceCalculator {

        @Override
        public double distanceMeters(GeoPoint a, GeoPoint b) {
            return 100.0;
        }
    }

    private record Input(List<OrderOfferGroup> groups, List<WaitingDreami> dreamis) {
    }

    private record BenchmarkResult(
            String policy,
            long elapsedNanos,
            long allocatedBytes,
            Map<String, Long> allocationClasses,
            Path recordingPath
    ) {
    }
}
