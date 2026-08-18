package com.naengsam.quick.domain.matching.policy.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * WaitingDreamiGrid가 반경 안의 드리미를 하나도 빠뜨리지 않고(상위집합 보장) 반환하면서, 반경 밖 드리미는 실제로
 * 걸러내는지 확인한다. 이 인덱스는 매칭 결과를 바꾸지 않는 프리필터이므로 "빠뜨리지 않는다"가 가장 중요한 성질이다.
 */
class WaitingDreamiGridTest {

    private static final GeoDistanceCalculator DISTANCE_CALCULATOR = new GeoDistanceCalculator();
    private static final GeoPoint SEOUL = point(37.5, 127.0);

    private static GeoPoint point(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private static WaitingDreami dreamiAt(GeoPoint location) {
        return new WaitingDreami(UUID.randomUUID(), location, WaitingDreamiStatus.MATCHING, LocalDateTime.now());
    }

    private static List<WaitingDreami> flatten(List<List<WaitingDreami>> buckets) {
        return buckets.stream().flatMap(List::stream).toList();
    }

    @Test
    void 같은_셀의_드리미는_후보로_반환된다() {
        WaitingDreami dreami = dreamiAt(point(37.5004, 127.0004));
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(List.of(dreami));

        List<WaitingDreami> found = flatten(grid.nearbyBuckets(SEOUL, 1_000));

        assertThat(found).containsExactly(dreami);
    }

    @Test
    void 반경_밖_먼_셀의_드리미는_반환되지_않는다() {
        // 약 55km 북쪽 — 어떤 offer scope로도 닿지 않는다.
        WaitingDreami dreami = dreamiAt(point(38.0, 127.0));
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(List.of(dreami));

        List<WaitingDreami> found = flatten(grid.nearbyBuckets(SEOUL, 6_000));

        assertThat(found).isEmpty();
    }

    /**
     * 중심이 셀 경계 바로 안쪽에 있고 드리미는 경계 바깥 셀에 있지만 직선거리로는 반경 안인 경우. 이웃 셀 범위를
     * 잘못 계산하면 여기서 후보가 사라진다.
     */
    @Test
    void 셀_경계_바로_바깥이지만_반경_안인_드리미도_반환된다() {
        GeoPoint center = point(37.5099, 127.0099);
        GeoPoint justOverTheBorder = point(37.5101, 127.0101);
        WaitingDreami dreami = dreamiAt(justOverTheBorder);
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(List.of(dreami));

        double distanceMeters = DISTANCE_CALCULATOR.distanceMeters(center, justOverTheBorder);
        List<WaitingDreami> found = flatten(grid.nearbyBuckets(center, 1_000));

        assertThat(distanceMeters).isLessThan(1_000);
        assertThat(found).containsExactly(dreami);
    }

    @Test
    void 반경이_1000에서_6000으로_넓어지면_더_먼_드리미도_포함된다() {
        // 중심에서 약 3.3km 북쪽 — 1000m scope에는 안 잡히고 6000m scope에는 잡혀야 한다.
        WaitingDreami dreami = dreamiAt(point(37.53, 127.0));
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(List.of(dreami));

        List<WaitingDreami> narrow = flatten(grid.nearbyBuckets(SEOUL, 1_000));
        List<WaitingDreami> wide = flatten(grid.nearbyBuckets(SEOUL, 6_000));

        assertThat(narrow).isEmpty();
        assertThat(wide).containsExactly(dreami);
    }

    @Test
    void 대기_드리미가_없으면_빈_목록을_반환한다() {
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(List.of());

        assertThat(grid.nearbyBuckets(SEOUL, 6_000)).isEmpty();
    }

    /**
     * 그리드의 유일한 존재 이유는 "후보를 줄이되 하나도 빠뜨리지 않는 것"이다. 한국 위경도 범위에 흩뿌린 드리미와
     * 중심점으로, 하버사인 기준 반경 안인 드리미가 전부 결과에 들어 있는지 확인한다. 시드를 고정해 결정적이다.
     */
    @Test
    void 무작위_좌표에서도_반경_안_드리미를_하나도_빠뜨리지_않는다() {
        Random random = new Random(20260818L);
        List<WaitingDreami> dreamis = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            dreamis.add(dreamiAt(randomKoreanPoint(random)));
        }
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(dreamis);

        for (int i = 0; i < 100; i++) {
            GeoPoint center = randomKoreanPoint(random);
            for (double radiusMeters : new double[] {1_000, 3_000, 6_000}) {
                Set<UUID> expected = dreamis.stream()
                        .filter(d -> DISTANCE_CALCULATOR.distanceMeters(center, d.location()) <= radiusMeters)
                        .map(WaitingDreami::dreamiId)
                        .collect(Collectors.toSet());

                Set<UUID> actual = flatten(grid.nearbyBuckets(center, radiusMeters)).stream()
                        .map(WaitingDreami::dreamiId)
                        .collect(Collectors.toSet());

                assertThat(actual).containsAll(expected);
            }
        }
    }

    /**
     * 드리미가 한 셀에 몰려도 버킷을 합쳐 복사하지 않아야 한다. 복사하면 주문마다 드리미 수만큼 참조를 새로 담게 되어
     * 도심에서 O(주문×드리미) 메모리를 쓴다.
     */
    @Test
    void 모든_드리미가_한_셀에_몰려도_버킷을_복사하지_않는다() {
        List<WaitingDreami> dreamis = List.of(dreamiAt(SEOUL), dreamiAt(SEOUL), dreamiAt(SEOUL));
        WaitingDreamiGrid grid = WaitingDreamiGrid.of(dreamis);

        List<List<WaitingDreami>> first = grid.nearbyBuckets(SEOUL, 1_000);
        List<List<WaitingDreami>> second = grid.nearbyBuckets(point(37.5001, 127.0001), 6_000);

        assertThat(first).hasSize(1);
        assertThat(first.getFirst()).hasSize(3);
        assertThat(second).contains(first.getFirst());
        assertThat(second.stream().filter(bucket -> bucket == first.getFirst())).hasSize(1);
    }

    private static GeoPoint randomKoreanPoint(Random random) {
        return point(33.0 + random.nextDouble() * 6.0, 125.0 + random.nextDouble() * 5.0);
    }
}
