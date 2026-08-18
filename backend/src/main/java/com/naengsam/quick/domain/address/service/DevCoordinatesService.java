package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * 전국 모드에서 주문이 떨어질 도시 중심들. 격자는 이 좌표를 <b>중심</b>으로 놓고 펴진다(강남 모드의
     * BASE_*는 격자 좌하단이고 중심이 37.4979/127.0276인 것과 같은 관계다).
     * <p><b>matchingtest-cell/modules/zones.mjs의 NATIONAL_ZONES와 같은 목록이어야 한다.</b> 부하테스트는
     * 드리미를 이 도시들에 뿌리는데, 목록이 어긋나면 주문 근처에 드리미가 없어 전 주문이 굶는다.
     */
    private static final Zone[] NATIONAL_ZONES = {
            // 강남 모드와 같은 중심. "서울 강남구 …" 주소는 전국 모드에서도 원래 자리에 떨어진다.
            new Zone("서울", 37.4979, 127.0276),
            new Zone("부산", 35.1578, 129.0594),
            new Zone("대구", 35.8686, 128.5940),
            new Zone("인천", 37.4487, 126.7016),
            new Zone("광주", 35.1520, 126.8515),
            new Zone("대전", 36.3510, 127.3845),
            new Zone("울산", 35.5384, 129.3114),
            new Zone("수원", 37.2636, 127.0286),
            new Zone("제주", 33.4890, 126.4983),
            new Zone("강릉", 37.7519, 128.8761),
            new Zone("전주", 35.8242, 127.1480),
            new Zone("청주", 36.6424, 127.4890),
            new Zone("천안", 36.8151, 127.1139),
            new Zone("창원", 35.2281, 128.6811),
            new Zone("포항", 36.0190, 129.3435),
            new Zone("춘천", 37.8813, 127.7298),
            new Zone("목포", 34.8118, 126.3922),
    };

    private static final double HALF_SPAN_LATITUDE = ROWS * CELL / 2;
    private static final double HALF_SPAN_LONGITUDE = COLUMNS * CELL / 2;

    private record Zone(String city, double latitude, double longitude) {
    }

    // 느린 카카오를 흉내 내는 인위적 지연. 기본 0 이라 평소에는 아무 영향이 없고, 부하테스트에서만 켠다.
    @Value("${kakao.dev-latency:0ms}")
    private Duration latency;

    /**
     * gangnam(기본) = 기존처럼 강남 일대 격자 하나. national = 주소의 첫 토큰(도시명)으로 위 NATIONAL_ZONES
     * 중 하나를 고른 뒤 그 도시 안 격자에 배치한다.
     * <p>전국 부하테스트(matchingtest-cell)에서만 national로 켠다. 일반 로컬 개발에서 켜면 주문이 전국에
     * 흩어져 클라이언트의 "근방 3km" 화면에서 사라진다.
     */
    @Value("${kakao.dev-zone-mode:gangnam}")
    private String zoneMode;

    /**
     * 어느 좌표 변환 구현이 떴는지 기동 로그로 남긴다. 이 스텁이 운영에 뜨면 모든 주소가 가짜 좌표로 처리되므로 INFO 가 아니라 WARN 이다.
     */
    @PostConstruct
    void init() {
        log.warn("좌표 변환 = 개발용 스텁 — 실제 좌표가 아니다 (kakao.enabled=false, 지연 {}ms, 분포 {})",
                latency.toMillis(), isNational() ? "전국 " + NATIONAL_ZONES.length + "개 도시" : "강남 일대");
    }

    /**
     * 주소 해시를 격자 한 칸에 대응시켜 좌표를 만든다. 서로 다른 주소가 같은 칸에 떨어지면(672칸 중 충돌) 출발지와 도착지가 같다고 판정돼 주문 접수가
     * SAME_ORIGIN_DESTINATION 으로 실패한다 — 결정적이므로 그때는 테스트 주소 목록을 바꾸면 된다.
     * <p>전국 모드에서는 격자를 놓을 도시를 주소의 첫 토큰으로 먼저 고른다. 한 주문의 출발지와 도착지가
     * 같은 도시 이름으로 시작하면 같은 도시 안에 떨어지므로, 도시를 가로지르는 수백 km짜리 배달이 생기지 않는다.
     */
    @Override
    public CoordinatesResponseDto getCoordinates(String roadAddress) {
        sleepArtificialLatency();

        int cell = Math.floorMod(roadAddress.hashCode(), ROWS * COLUMNS);
        double baseLatitude = BASE_LATITUDE;
        double baseLongitude = BASE_LONGITUDE;
        if (isNational()) {
            Zone zone = zoneOf(roadAddress);
            baseLatitude = zone.latitude() - HALF_SPAN_LATITUDE;
            baseLongitude = zone.longitude() - HALF_SPAN_LONGITUDE;
        }
        double latitude = baseLatitude + (cell / COLUMNS) * CELL;
        double longitude = baseLongitude + (cell % COLUMNS) * CELL;

        log.debug("[DEV-GEOCODE] address={} lat={} lng={}", roadAddress, latitude, longitude);

        // 실제 카카오 응답과 같은 모양으로 채운다. 호출부는 documents[0].roadAddress 의 x(경도)/y(위도)만 읽는다.
        CoordinatesResponseDto.RoadAddress address = new CoordinatesResponseDto.RoadAddress(
                roadAddress, "서울", "개발구", "개발동", "개발로", "1", "", "개발빌딩", "00000",
                String.format("%.8f", longitude), String.format("%.8f", latitude));
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(address)));
    }

    private boolean isNational() {
        return "national".equalsIgnoreCase(zoneMode);
    }

    /**
     * 주소가 떨어질 도시를 고른다. 첫 토큰이 NATIONAL_ZONES의 도시명과 같으면 그 도시를, 아니면 주소 해시로
     * 아무 도시나 고른다. 후자는 부하테스트가 만들지 않은 주소(예: 브라우저에서 손으로 넣은 주소)에도
     * 좌표를 주기 위한 폴백이라, 그 경우 출발지와 도착지가 다른 도시에 떨어질 수 있다.
     */
    private static Zone zoneOf(String roadAddress) {
        int space = roadAddress.indexOf(' ');
        String head = space < 0 ? roadAddress : roadAddress.substring(0, space);
        for (Zone zone : NATIONAL_ZONES) {
            if (zone.city().equals(head)) {
                return zone;
            }
        }
        return NATIONAL_ZONES[Math.floorMod(head.hashCode(), NATIONAL_ZONES.length)];
    }

    private void sleepArtificialLatency() {
        if (latency.isZero() || latency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(latency.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
