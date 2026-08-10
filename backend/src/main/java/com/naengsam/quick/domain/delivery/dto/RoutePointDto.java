package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 추천 이동경로의 좌표 한 점. 카카오 길찾기가 그린 도보 경로를 지도에 폴리라인으로 그리는 데 쓴다.
 */
public record RoutePointDto(
        @Schema(description = "위도", example = "37.49864277")
        double latitude,

        @Schema(description = "경도", example = "127.02700693")
        double longitude
) {
    /**
     * 카카오 Route 를 좌표 목록으로 평탄화한다. path.points 는 [x=경도, y=위도] 순서이므로 뒤집어 (위도, 경도)로 정규화한다.
     */
    public static List<RoutePointDto> from(KakaoDirectionsResponseDto.Route route) {
        List<RoutePointDto> points = new ArrayList<>();
        if (route == null || route.legs() == null) {
            return points;
        }
        for (KakaoDirectionsResponseDto.Leg leg : route.legs()) {
            if (leg == null || leg.steps() == null) {
                continue;
            }
            for (KakaoDirectionsResponseDto.Step step : leg.steps()) {
                if (step == null || step.path() == null || step.path().points() == null) {
                    continue;
                }
                for (double[] point : step.path().points()) {
                    if (point == null || point.length < 2) {
                        continue;
                    }
                    points.add(new RoutePointDto(point[1], point[0]));
                }
            }
        }
        return points;
    }
}
