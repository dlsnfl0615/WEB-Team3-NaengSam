package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 도보 길찾기 결과를 Redis 에 캐싱하는 데코레이터(#435). 주소를 그대로 둔 채 물품 유형·크기만 바꿔도 예상 요금 조회가 매번 경로를 다시 물어보던 것을 막는다(물품 배율은 경로와 무관한
 * 순수 곱셈이다).
 * <p>
 * 배선 근거는 {@link CachedCoordinatesService} 와 같다(구체 타입 델리게이트, {@code @Primary} 필수, {@code kakao.enabled=false} 면 미등록).
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "true", matchIfMissing = true)
public class CachedDirectionsService implements DirectionsService {

    private static final String KEY_PREFIX = "kakao:route:";
    private static final String API = "route";

    private final KakaoDirectionsService delegate;
    private final KakaoResponseCache cache;

    /**
     * 경로 값은 폴리라인({@code Path.points}) 때문에 지오코딩의 30배 가까이 커서(5~15KB), 히트율보다 메모리 상한이 우선이다. #435 가 지적한 중복은 전부 한 주문 흐름
     * 안(초~분)에서 벌어지므로 1시간이면 사실상 다 잡는다. 0 이하면 캐시를 건너뛴다(킬 스위치).
     */
    @Value("${kakao.cache.route-ttl:1h}")
    private Duration ttl;

    @PostConstruct
    void init() {
        if (isDisabled()) {
            log.warn("도보 길찾기 캐시 = 꺼짐 (kakao.cache.route-ttl={})", ttl);
            return;
        }
        log.info("도보 길찾기 캐시 = Redis (TTL {})", ttl);
    }

    @Override
    public KakaoDirectionsResponseDto.Route getRoute(GeoPoint origin, GeoPoint destination) {
        if (isDisabled()) {
            return delegate.getRoute(origin, destination);
        }

        String key = key(origin, destination);
        KakaoDirectionsResponseDto.Route cached = cache.get(key, KakaoDirectionsResponseDto.Route.class, API);
        if (cached != null) {
            return cached;
        }

        KakaoDirectionsResponseDto.Route fresh = delegate.getRoute(origin, destination);
        cache.put(key, fresh, ttl, API);
        return fresh;
    }

    /**
     * 출발지·도착지 순서는 정렬하지 않는다. 도보 경로는 비대칭이라(일방통행·계단·육교) 뒤집으면 거리·시간뿐 아니라 폴리라인 방향까지 달라진다.
     */
    private String key(GeoPoint origin, GeoPoint destination) {
        return KEY_PREFIX + plain(origin.latitude()) + ":" + plain(origin.longitude())
                + ":" + plain(destination.latitude()) + ":" + plain(destination.longitude());
    }

    /**
     * 같은 지점이 항상 같은 키가 되도록 scale 을 지운다. 좌표는 유입 경로마다 scale 이 다르다 — 지오코딩은 카카오 응답 문자열 그대로(가변), DB 경유는 컬럼 정의대로 8 이다.
     * {@code 37.4979} 와 {@code 37.49790000} 은 같은 지점이지만 {@code BigDecimal.equals} 는 false 라, 이 정규화가 없으면 두 경로가 서로의
     * 캐시를 절대 못 쓴다. 반올림이 아니라 후행 0 제거라 값의 의미는 그대로다.
     */
    private String plain(BigDecimal coordinate) {
        return coordinate.stripTrailingZeros().toPlainString();
    }

    private boolean isDisabled() {
        return ttl.isZero() || ttl.isNegative();
    }
}
