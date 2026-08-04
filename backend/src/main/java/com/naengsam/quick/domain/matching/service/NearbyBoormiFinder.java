package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyBoormiDto;
import com.naengsam.quick.domain.matching.dto.NearbyBoormiRequest;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기준 좌표 반경 내 대기중인 부르미를 거리순으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class NearbyBoormiFinder {

    private static final int MAX_NEARBY_BOORMI_COUNT = 10;

    private final MatchingService matchingService;
    private final BoormiService boormiService;

    /**
     * 기준 좌표에서 반경(m) 이내에 있는 대기중인 부르미를 가까운 순으로 조회한다. 대기중인 부르미는 별도 등록 큐가 없고,
     * 매칭 시작 후(OPEN) 아직 확정되지 않은 주문에서 {@link MatchingService#waitingBoormis()}로 도출된다.
     *
     * @param request 기준 좌표({@code lat}/{@code lng}), 조회 반경(m, {@code radius}), 최대 조회 개수({@code count})
     * @return 반경 내 부르미를 거리순으로 최대 {@code min(request.count(), 10)}명까지 담은 리스트. 결과가 없으면 빈 리스트.
     */
    public List<NearbyBoormiDto> find(NearbyBoormiRequest request) {
        GeoPoint origin = new GeoPoint(request.lat(), request.lng());
        int limit = Math.min(request.count(), MAX_NEARBY_BOORMI_COUNT);

        return matchingService.waitingBoormis().stream()
                .map(boormi -> NearbyBoormiDto.from(boormi, boormiService.distanceMeters(origin, boormi.location())))
                .filter(dto -> dto.distanceMeters() <= request.radius())
                .sorted(Comparator.comparingDouble(NearbyBoormiDto::distanceMeters))
                .limit(limit)
                .toList();
    }
}
