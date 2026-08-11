package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 로컬/부하테스트용 도보 경로 계산기. 외부 호출 없이 두 좌표의 직선거리에서 보행 거리·시간을 추정한다. {@code kakao.enabled=false} 일 때 활성화된다(크레덴셜 불필요).
 * <p>
 * 고정값을 돌려주지 않는 이유: 요금 계산({@code BoormiService.calculatePrice})의 거리 구간 로직이 실제로 돌아야 주문마다 거리에 비례한 금액과 ETA 가 나온다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "false")
public class DevDirectionsService implements DirectionsService {

    private static final double EARTH_RADIUS = 6_371_000;   // m
    private static final double DETOUR_RATIO = 1.3;         // 직선거리 대비 실제 보행 경로 우회율
    private static final double WALK_SPEED = 1.2;           // m/s

    @Override
    public KakaoDirectionsResponseDto.Route getRoute(GeoPoint origin, GeoPoint destination) {
        int totalDistance = (int) Math.round(distanceMeters(origin, destination) * DETOUR_RATIO);
        int totalTime = (int) Math.round(totalDistance / WALK_SPEED);

        log.debug("[DEV-DIRECTIONS] distance={}m time={}s", totalDistance, totalTime);
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(totalDistance, totalTime),
                straightLegs(origin, destination, totalDistance, totalTime));
    }

    /**
     * 출발지→도착지를 잇는 직선 한 구간을 카카오 응답과 같은 구조로 만든다. 실제 도로를 따르지는 않지만 주문에 저장되는 경로 좌표(routePath)가 비지 않아 로컬에서도 추적 지도가 폴리라인을
     * 그린다.
     */
    private KakaoDirectionsResponseDto.Leg[] straightLegs(GeoPoint origin, GeoPoint destination,
            int totalDistance, int totalTime) {
        KakaoDirectionsResponseDto.Path path = new KakaoDirectionsResponseDto.Path(new double[][]{
                {origin.longitude().doubleValue(), origin.latitude().doubleValue()},
                {destination.longitude().doubleValue(), destination.latitude().doubleValue()}
        });
        KakaoDirectionsResponseDto.Step step = new KakaoDirectionsResponseDto.Step(
                new KakaoDirectionsResponseDto.StepProperties(totalDistance, "직선 이동", totalTime,
                        origin.longitude().doubleValue(), origin.latitude().doubleValue()),
                path);

        return new KakaoDirectionsResponseDto.Leg[]{
                new KakaoDirectionsResponseDto.Leg(
                        new KakaoDirectionsResponseDto.LegProperties(totalDistance, totalTime),
                        new KakaoDirectionsResponseDto.Step[]{step})
        };
    }

    /**
     * 두 좌표 사이의 하버사인 직선거리(m).
     */
    private double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());

        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(h));
    }
}
