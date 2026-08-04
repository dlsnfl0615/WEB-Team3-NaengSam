package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기준 좌표 반경 내 대기중인 주문을 거리순으로 조회한다. 한 부르미가 여러 주문을 동시에 가질 수 있으므로 부르미 단위가 아니라 주문 단위로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class NearbyOrderFinder {

    private static final int MAX_NEARBY_ORDER_COUNT = 10;

    private final MatchingService matchingService;
    private final BoormiService boormiService;

    /**
     * 기준 좌표에서 반경(m) 이내에 있는 대기중인 주문을 가까운 순으로 조회한다. 대기중인 주문은 별도 등록 큐가 없고, 매칭 시작 후(OPEN) 아직 확정되지 않은
     * 주문에서 {@link MatchingService#waitingOrders()}로 도출된다.
     *
     * @param request 기준 좌표({@code lat}/{@code lng}), 조회 반경(m, {@code radius}), 최대 조회 개수({@code count})
     * @return 반경 내 주문을 거리순으로 최대 {@code min(request.count(), 10)}개까지 담은 리스트. 결과가 없으면 빈 리스트.
     */
    public List<NearbyOrderDto> find(NearbyOrderRequest request) {
        GeoPoint origin = new GeoPoint(request.lat(), request.lng());
        int limit = Math.min(request.count(), MAX_NEARBY_ORDER_COUNT);

        return matchingService.waitingOrders().stream()
                .map(order -> NearbyOrderDto.from(order, boormiService.distanceMeters(origin, order.location())))
                .filter(dto -> dto.distanceMeters() <= request.radius())
                .sorted(Comparator.comparingDouble(NearbyOrderDto::distanceMeters))
                .limit(limit)
                .toList();
    }
}
