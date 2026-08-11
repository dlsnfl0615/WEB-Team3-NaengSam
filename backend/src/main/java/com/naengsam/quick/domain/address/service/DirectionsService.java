package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.matching.dto.GeoPoint;

/**
 * 도보 경로 조회 추상화. 운영은 {@link KakaoDirectionsService}, 로컬 개발·부하테스트는 {@link DevDirectionsService} 가 주입된다.
 */
public interface DirectionsService {

    KakaoDirectionsResponseDto.Route getRoute(GeoPoint origin, GeoPoint destination);
}
