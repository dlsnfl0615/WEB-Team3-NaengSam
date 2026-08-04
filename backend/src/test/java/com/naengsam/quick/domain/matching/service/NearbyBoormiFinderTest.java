package com.naengsam.quick.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.boormi.service.BoormiService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyBoormiDto;
import com.naengsam.quick.domain.matching.dto.NearbyBoormiRequest;
import com.naengsam.quick.domain.matching.service.MatchingService.WaitingBoormi;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 반경 내 부르미 위치 조회 기능이 반경/개수 상한을 지키고 거리순으로 결과를 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NearbyBoormiFinderTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private BoormiService boormiService;
    @InjectMocks
    private NearbyBoormiFinder nearbyBoormiFinder;

    private NearbyBoormiRequest request() {
        return new NearbyBoormiRequest(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), 1000.0, 20);
    }

    private WaitingBoormi waitingBoormi(UUID boormiId) {
        return new WaitingBoormi(boormiId, new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)));
    }

    @Test
    void 반경_밖에_있는_부르미는_결과에서_제외된다() {
        UUID inRange = UUID.randomUUID();
        UUID outOfRange = UUID.randomUUID();
        given(matchingService.waitingBoormis()).willReturn(List.of(waitingBoormi(inRange), waitingBoormi(outOfRange)));
        given(boormiService.distanceMeters(any(), any()))
                .willReturn(500.0)
                .willReturn(1500.0);

        List<NearbyBoormiDto> result = nearbyBoormiFinder.find(request());

        assertThat(result).extracting(NearbyBoormiDto::boormiId).containsExactly(inRange);
    }

    @Test
    void 가까운_부르미부터_거리순으로_정렬된다() {
        UUID far = UUID.randomUUID();
        UUID near = UUID.randomUUID();
        given(matchingService.waitingBoormis()).willReturn(List.of(waitingBoormi(far), waitingBoormi(near)));
        given(boormiService.distanceMeters(any(), any()))
                .willReturn(800.0)
                .willReturn(200.0);

        List<NearbyBoormiDto> result = nearbyBoormiFinder.find(request());

        assertThat(result).extracting(NearbyBoormiDto::boormiId).containsExactly(near, far);
    }

    @Test
    void count가_10을_초과해도_최대_10명까지만_반환된다() {
        List<WaitingBoormi> boormis = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> waitingBoormi(UUID.randomUUID()))
                .toList();
        given(matchingService.waitingBoormis()).willReturn(boormis);
        given(boormiService.distanceMeters(any(), any())).willReturn(100.0);

        List<NearbyBoormiDto> result = nearbyBoormiFinder.find(request());

        assertThat(result).hasSize(10);
    }

    @Test
    void 대기중인_부르미가_없으면_빈_리스트를_반환한다() {
        given(matchingService.waitingBoormis()).willReturn(List.of());

        List<NearbyBoormiDto> result = nearbyBoormiFinder.find(request());

        assertThat(result).isEmpty();
    }
}
