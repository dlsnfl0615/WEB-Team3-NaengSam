package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 주소 → 좌표 변환 결과를 Redis 에 캐싱하는 데코레이터(#435). 한 주문 흐름에서 같은 주소가 {@code /address/place} →
 * {@code /expected-value} → 콜 등록으로 최소 3번 지오코딩되던 것을 1번으로 줄인다.
 * <p>
 * 델리게이트로 인터페이스가 아니라 구체 타입 {@link KakaoCoordinatesService} 를 받는다 — {@code @Primary} 가 자기 자신을 주입하는 순환을 원천 차단한다.
 * {@code @Primary} 는 필수다. 없으면 {@code CoordinatesService} 빈이 둘이 되어 주입 지점들이 {@code NoUniqueBeanDefinitionException} 으로
 * 기동을 실패한다. {@code kakao.enabled=false} 일 때는 이 빈도, 델리게이트도 뜨지 않고 {@code DevCoordinatesService} 만 남는다(부하테스트의 스텁 지연 재현
 * 보존).
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "true", matchIfMissing = true)
public class CachedCoordinatesService implements CoordinatesService {

    private static final String KEY_PREFIX = "kakao:geo:";
    private static final String API = "geocode";

    private final KakaoCoordinatesService delegate;
    private final KakaoResponseCache cache;

    /**
     * 주소→좌표는 사실상 불변이고 값도 작아(~400B) 길게 잡는다. 0 이하면 캐시를 통째로 건너뛴다 — 재배포 없이 끌 수 있는 킬 스위치다.
     */
    @Value("${kakao.cache.geocode-ttl:7d}")
    private Duration ttl;

    @PostConstruct
    void init() {
        if (isDisabled()) {
            log.warn("좌표 변환 캐시 = 꺼짐 (kakao.cache.geocode-ttl={})", ttl);
            return;
        }
        log.info("좌표 변환 캐시 = Redis (TTL {})", ttl);
    }

    @Override
    public CoordinatesResponseDto getCoordinates(String roadAddress) {
        if (isDisabled()) {
            return delegate.getCoordinates(roadAddress);
        }

        String key = KEY_PREFIX + normalize(roadAddress);
        CoordinatesResponseDto cached = cache.get(key, CoordinatesResponseDto.class, API);
        if (cached != null) {
            return cached;
        }

        // 카카오 실패(BusinessException)는 그대로 전파되어 put 에 닿지 않는다 — 일시적 장애를 TTL 만큼 연장하지 않기 위함.
        // 반대로 documents 가 빈 정상 응답(주소 없음)은 캐싱한다. 호출부가 isEmpty() 로 이미 처리하는 정상 결과다.
        CoordinatesResponseDto fresh = delegate.getCoordinates(roadAddress);
        cache.put(key, fresh, ttl, API);
        return fresh;
    }

    /**
     * 키에만 쓰는 정규화. 같은 주소가 화면마다 다른 공백으로 들어와 히트율이 조용히 떨어지는 걸 막는다. 델리게이트에는 원본 문자열을 그대로 넘겨 카카오 응답을 바꾸지 않는다. 소문자화는 하지
     * 않는다(한글 주소라 의미가 없다).
     */
    private String normalize(String roadAddress) {
        return roadAddress.trim().replaceAll("\\s+", " ");
    }

    private boolean isDisabled() {
        return ttl.isZero() || ttl.isNegative();
    }
}
