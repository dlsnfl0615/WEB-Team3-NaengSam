package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대기 드리미를 위경도 격자(셀)에 나눠 담아, 주문 하나가 반경 안의 드리미를 찾을 때 전국을 훑지 않게 해주는 공간 인덱스.
 * <p>{@link MatchingAssignmentProblemAssembler}의 거리 필터는 하버사인을 계산한 <b>뒤</b>에 적용되므로, 이 인덱스가
 * 없으면 반경 1km 정책이라도 전국 모든 드리미와 거리를 계산하게 된다. 이 클래스는 그 앞단에서 반경을 덮는 셀만 골라
 * 후보를 좁힌다. 최종 판정은 여전히 호출자의 하버사인이 하므로 <b>매칭 결과는 달라지지 않는다.</b>
 * <p>배치 사이클마다 새로 만든다. 어떤 드리미가 후보인지는 상태(MATCHING)뿐 아니라 SSE 연결 생존 여부로도 결정되는데,
 * 후자는 이벤트가 아니라 사이클 시점에 조회되는 값이라 증분 인덱스로는 반영할 수 없다. 이미 필터링이 끝난 목록을 받아
 * 매번 버킷팅하면 정합성 문제가 없고, 비용은 드리미 수만큼의 맵 삽입뿐이다.
 * <p>한계: 경도 ±180° 자오선을 넘는 조회는 셀 인덱스가 불연속이라 덮지 못한다. 국내 서비스라 발생하지 않는다.
 * <p>좌표가 없는(null) 드리미/중심점은 조용히 제외한다 — {@link com.naengsam.quick.domain.matching.service.MatchingService#pickupEtaMinutesForOffer}가
 * 이미 전제하는 것처럼, 좌표 없는 드리미는 존재할 수 있고 이 인덱스가 배치 전체를 실패시킬 이유는 없다.
 */
final class WaitingDreamiGrid {

    /**
     * 셀 한 변의 크기(도). 한국 기준 위도 약 1.11km / 경도 약 0.88km.
     * <p>가장 좁은 offer scope(1000m)에서 훑는 셀이 15개, 가장 넓은 scope(6000m)에서 195개가 되는 값이다. 셀을 더
     * 잘게 쪼개면 훑는 면적은 줄지만 비어 있는 셀을 조회하는 횟수가 급격히 늘어난다.
     */
    private static final double CELL_DEGREES = 0.01;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    /** 극지방에서 cos가 0에 수렴해 경도 셀 폭이 발산하는 것을 막는 상한. 국내 좌표에서는 걸리지 않는다. */
    private static final double MAX_ABS_LAT = 85.0;

    private final Map<Long, List<WaitingDreami>> buckets;

    private WaitingDreamiGrid(Map<Long, List<WaitingDreami>> buckets) {
        this.buckets = buckets;
    }

    static WaitingDreamiGrid of(List<WaitingDreami> dreamis) {
        Map<Long, List<WaitingDreami>> buckets = new HashMap<>();
        for (WaitingDreami dreami : dreamis) {
            GeoPoint location = dreami.location();
            if (!hasCoordinates(location)) {
                continue;
            }
            long key = cellKey(
                    cellIndex(location.latitude().doubleValue()),
                    cellIndex(location.longitude().doubleValue()));
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(dreami);
        }
        return new WaitingDreamiGrid(buckets);
    }

    /**
     * {@code center}에서 {@code radiusMeters} 이내인 드리미를 <b>빠짐없이</b> 포함하는 버킷들을 반환한다. 반경을 덮는
     * 셀 단위로 고르므로 반경 밖 드리미가 섞여 있는 상위집합이며, 정확한 거리 판정은 호출자가 한다.
     * <p>버킷을 하나의 목록으로 합치지 않고 그대로 넘긴다. 드리미가 한 셀에 몰린 경우(도심) 합치면 주문마다 드리미
     * 수만큼 참조를 복사해 O(주문×드리미) 메모리를 쓰게 되지만, 이 형태면 주문당 할당이 드리미 수와 무관하게 훑는 셀
     * 개수로 고정된다.
     */
    List<List<WaitingDreami>> nearbyBuckets(GeoPoint center, double radiusMeters) {
        if (!hasCoordinates(center)) {
            return List.of();
        }

        double latitude = center.latitude().doubleValue();
        double longitude = center.longitude().doubleValue();

        // 반경 안의 점은 위도로 최대 radiusDegreesLat만큼 떨어져 있다.
        double radiusDegreesLat = radiusMeters / METERS_PER_DEGREE_LAT;
        // 경도 1도의 길이는 고위도로 갈수록 짧아지므로, 반경이 닿는 가장 고위도를 기준으로 잡아야 링 수가 반경을
        // 확실히 덮는다. 여기서 중심 위도를 그대로 쓰면 반경 끝의 좁아진 셀을 놓칠 수 있다.
        double worstLatitude = Math.min(Math.abs(latitude) + radiusDegreesLat, MAX_ABS_LAT);
        double metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(worstLatitude));

        int latRings = (int) Math.ceil(radiusDegreesLat / CELL_DEGREES);
        int lngRings = (int) Math.ceil(radiusMeters / metersPerDegreeLng / CELL_DEGREES);
        int centerLatIndex = cellIndex(latitude);
        int centerLngIndex = cellIndex(longitude);

        List<List<WaitingDreami>> result = new ArrayList<>();
        for (int latOffset = -latRings; latOffset <= latRings; latOffset++) {
            for (int lngOffset = -lngRings; lngOffset <= lngRings; lngOffset++) {
                List<WaitingDreami> bucket =
                        buckets.get(cellKey(centerLatIndex + latOffset, centerLngIndex + lngOffset));
                if (bucket != null) {
                    result.add(bucket);
                }
            }
        }
        return result;
    }

    private static int cellIndex(double degrees) {
        return (int) Math.floor(degrees / CELL_DEGREES);
    }

    private static long cellKey(int latIndex, int lngIndex) {
        return ((long) latIndex << 32) | (lngIndex & 0xFFFFFFFFL);
    }

    private static boolean hasCoordinates(GeoPoint point) {
        return point != null && point.latitude() != null && point.longitude() != null;
    }
}
