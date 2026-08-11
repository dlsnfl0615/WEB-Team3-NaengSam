package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import org.springframework.stereotype.Component;

@Component
public class GeoDistanceCalculator {

    private static final double EARTH_RADIUS = 6_371_000;  // 지구 반지름(m)

    /**
     * 두 좌표 사이의 하버사인 직선거리(m)를 계산한다.
     */
    public double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(h));
    }
}
