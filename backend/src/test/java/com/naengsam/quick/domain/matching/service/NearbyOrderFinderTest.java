package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.model.WaitingOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 반경 내 주문 위치 조회 기능이 반경/개수 상한을 지키고 거리순으로 결과를 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NearbyOrderFinderTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private GeoDistanceCalculator geoDistanceCalculator;
    @InjectMocks
    private NearbyOrderFinder nearbyOrderFinder;

    private NearbyOrderRequest request() {
        return new NearbyOrderRequest(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), 1000.0, 20);
    }

    private WaitingOrder waitingOrder(UUID orderId) {
        return new WaitingOrder(orderId, new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)));
    }

    @Test
    void 반경_밖에_있는_주문은_결과에서_제외된다() {
        UUID inRange = UUID.randomUUID();
        UUID outOfRange = UUID.randomUUID();
        given(matchingService.waitingOrders()).willReturn(List.of(waitingOrder(inRange), waitingOrder(outOfRange)));
        given(geoDistanceCalculator.distanceMeters(any(), any()))
                .willReturn(500.0)
                .willReturn(1500.0);

        List<NearbyOrderDto> result = nearbyOrderFinder.find(request());

        assertThat(result).extracting(NearbyOrderDto::orderId).containsExactly(inRange);
    }

    @Test
    void 가까운_주문부터_거리순으로_정렬된다() {
        UUID far = UUID.randomUUID();
        UUID near = UUID.randomUUID();
        given(matchingService.waitingOrders()).willReturn(List.of(waitingOrder(far), waitingOrder(near)));
        given(geoDistanceCalculator.distanceMeters(any(), any()))
                .willReturn(800.0)
                .willReturn(200.0);

        List<NearbyOrderDto> result = nearbyOrderFinder.find(request());

        assertThat(result).extracting(NearbyOrderDto::orderId).containsExactly(near, far);
    }

    @Test
    void count가_10을_초과해도_최대_10개까지만_반환된다() {
        List<WaitingOrder> orders = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> waitingOrder(UUID.randomUUID()))
                .toList();
        given(matchingService.waitingOrders()).willReturn(orders);
        given(geoDistanceCalculator.distanceMeters(any(), any())).willReturn(100.0);

        List<NearbyOrderDto> result = nearbyOrderFinder.find(request());

        assertThat(result).hasSize(10);
    }

    @Test
    void 대기중인_주문이_없으면_빈_리스트를_반환한다() {
        given(matchingService.waitingOrders()).willReturn(List.of());

        List<NearbyOrderDto> result = nearbyOrderFinder.find(request());

        assertThat(result).isEmpty();
    }
}
