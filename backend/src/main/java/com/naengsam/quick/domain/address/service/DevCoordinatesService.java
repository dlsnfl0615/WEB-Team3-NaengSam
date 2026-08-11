package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 로컬/부하테스트용 좌표 변환기. 외부 호출 없이 주소 문자열에서 좌표를 만든다. {@code kakao.enabled=false} 일 때 활성화된다(크레덴셜 불필요).
 * <p>
 * 견적 조회({@code /address/place})와 주문 접수({@code /boormi/calls})가 같은 주소에 같은 좌표를 줘야 하므로 난수를 쓰지 않고 주소 해시로 결정한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "false")
public class DevCoordinatesService implements CoordinatesService {

    // 강남 일대(중심 37.4979/127.0276) 반경 약 1.5km 격자. 실제 서비스 주소가 모이는 범위와 맞춰야
    // 클라이언트의 "근방 3km" 화면이 정상 동작한다 — 범위를 넓게 잡으면 주문이 화면에서 사라진다.
    // 간격 0.001도는 위도 약 111m, 경도 약 88m 로 출발지-도착지 최소 직선거리(50m) 가드보다 크다.
    private static final double BASE_LATITUDE = 37.486;   // 37.486 ~ 37.509
    private static final double BASE_LONGITUDE = 127.014; // 127.014 ~ 127.041
    private static final double CELL = 0.001;
    private static final int ROWS = 24;
    private static final int COLUMNS = 28;

    /**
     * 주소 해시를 격자 한 칸에 대응시켜 좌표를 만든다. 서로 다른 주소가 같은 칸에 떨어지면(672칸 중 충돌) 출발지와 도착지가 같다고 판정돼 주문 접수가
     * SAME_ORIGIN_DESTINATION 으로 실패한다 — 결정적이므로 그때는 테스트 주소 목록을 바꾸면 된다.
     */
    @Override
    public CoordinatesResponseDto getCoordinates(String roadAddress) {
        int cell = Math.floorMod(roadAddress.hashCode(), ROWS * COLUMNS);
        double latitude = BASE_LATITUDE + (cell / COLUMNS) * CELL;
        double longitude = BASE_LONGITUDE + (cell % COLUMNS) * CELL;

        log.debug("[DEV-GEOCODE] address={} lat={} lng={}", roadAddress, latitude, longitude);

        // 실제 카카오 응답과 같은 모양으로 채운다. 호출부는 documents[0].roadAddress 의 x(경도)/y(위도)만 읽는다.
        CoordinatesResponseDto.RoadAddress address = new CoordinatesResponseDto.RoadAddress(
                roadAddress, "서울", "개발구", "개발동", "개발로", "1", "", "개발빌딩", "00000",
                String.format("%.8f", longitude), String.format("%.8f", latitude));
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(address)));
    }
}
