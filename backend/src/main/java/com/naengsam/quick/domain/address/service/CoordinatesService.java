package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;

/**
 * 도로명주소 → 좌표 변환 추상화. 운영은 {@link KakaoCoordinatesService}, 로컬 개발·부하테스트는 {@link DevCoordinatesService} 가 주입된다.
 */
public interface CoordinatesService {

    CoordinatesResponseDto getCoordinates(String roadAddress);
}
