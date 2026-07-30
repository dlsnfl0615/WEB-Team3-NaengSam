package com.naengsam.quick.domain.boormi.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.domain.address.service.KakaoDirectionsService;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoormiService {

    private static final int BASE_SECTION = 1500;   // 기본 구간(m)
    private static final int UNIT_DISTANCE = 100;   // 과금 단위(m)
    private static final int BASE_RATE = 100;       // 기본 구간 100m당 요금(원)
    private static final int BASE_FEE = 3000;       // 기본요금 3000원
    private static final int OVER_RATE = 160;       // 초과 구간 100m당 요금(원)

    private final BoormiRepository boormiRepository;
    private final CoordinatesService coordinatesService;
    private final KakaoDirectionsService kakaoDirectionsService;

    public void subscribeOrder(OrderRequest orderRequest) {
    }

    /**
     * 출발지/도착지 도로명주소를 좌표로 변환한 뒤 카카오 길찾기로 실제 거리·소요시간을 구하고, 물건 유형 배율을 반영한 예상 가격/시간/거리를 반환한다.
     */
    @Transactional(readOnly = true)
    public ExpectedValueDto expectedValue(ExpectedValueRequest request) {
        GeoPoint origin = toGeoPoint(request.originAddressLine1());
        GeoPoint destination = toGeoPoint(request.destinationAddressLine1());

        KakaoDirectionsResponseDto.Properties route = kakaoDirectionsService.getRoute(origin, destination);

        int expectedValue = calPrice(route.totalDistance(), request.itemCd());
        int expectedTime = (int) Math.ceil(route.totalTime() / 60.0);

        return new ExpectedValueDto(expectedValue, expectedTime, route.totalDistance());
    }

    private GeoPoint toGeoPoint(String roadAddress) {
        CoordinatesResponseDto coordinates = coordinatesService.getCoordinates(roadAddress);
        List<CoordinatesResponseDto.Document> documents = coordinates.documents();
        if (documents.isEmpty()) {
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        CoordinatesResponseDto.RoadAddress address = documents.getFirst().roadAddress();
        return new GeoPoint(Double.parseDouble(address.y()), Double.parseDouble(address.x()));
    }

    /**
     * 거리(m)에 따라 요금을 계산한다. 기본 1.5km까지는 100m당 100원, 초과 구간은 100m당 160원으로 과금하고 물건 유형 배율을 곱한다.
     */
    private int calPrice(int distance, ItemCd itemCd) {
        int baseDistance = Math.min(distance, BASE_SECTION);
        int overDistance = Math.max(distance - BASE_SECTION, 0);

        int price = (baseDistance / UNIT_DISTANCE) * BASE_RATE
                + (overDistance / UNIT_DISTANCE) * OVER_RATE
                + BASE_FEE;

        return (int) Math.round(price * ItemCd.multiplier(itemCd));
    }
}

