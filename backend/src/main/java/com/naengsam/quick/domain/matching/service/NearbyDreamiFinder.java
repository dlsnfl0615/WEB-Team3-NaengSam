package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiDto;
import com.naengsam.quick.domain.matching.dto.NearbyDreamiRequest;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기준 좌표 반경 내 대기중인 드리미를 거리순으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class NearbyDreamiFinder {

    private static final int MAX_NEARBY_DREAMI_COUNT = 10;

    private final MatchingService matchingService;
    private final GeoDistanceCalculator geoDistanceCalculator;

    /**
     * 기준 좌표에서 반경(m) 이내에 있는 대기중인 드리미를 가까운 순으로 조회한다. 대기중인 드리미는 {@link MatchingService#waitingDreamis()}에서 가져온다.
     *
     * @param request 기준 좌표({@code lat}/{@code lng}), 조회 반경(m, {@code radius}), 최대 조회 개수({@code count})
     * @return 반경 내 드리미를 거리순으로 최대 {@code min(request.count(), 10)}명까지 담은 리스트. 결과가 없으면 빈 리스트.
     */
    public List<NearbyDreamiDto> find(NearbyDreamiRequest request) {
        GeoPoint origin = new GeoPoint(request.lat(), request.lng());
        int limit = Math.min(request.count(), MAX_NEARBY_DREAMI_COUNT);

        return matchingService.waitingDreamis().stream()
                .map(dreami -> NearbyDreamiDto.from(dreami,
                        geoDistanceCalculator.distanceMeters(origin, dreami.location())))
                .filter(dto -> dto.distanceMeters() <= request.radius())
                .sorted(Comparator.comparingDouble(NearbyDreamiDto::distanceMeters))
                .limit(limit)
                .toList();
    }
}
