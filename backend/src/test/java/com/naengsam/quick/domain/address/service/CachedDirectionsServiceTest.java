package com.naengsam.quick.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 길찾기 캐시 데코레이터의 히트/미스/실패 처리와, scale 이 달라도 같은 좌표면 같은 키가 되는지(이 설계의 핵심)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CachedDirectionsServiceTest {

    private static final Duration TTL = Duration.ofHours(1);

    @Mock
    private KakaoDirectionsService delegate;
    @Mock
    private KakaoResponseCache cache;
    @InjectMocks
    private CachedDirectionsService cachedDirectionsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cachedDirectionsService, "ttl", TTL);
    }

    @Test
    void 캐시에_값이_있으면_카카오를_호출하지_않는다() {
        KakaoDirectionsResponseDto.Route cached = route();
        given(cache.get(anyString(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"))).willReturn(cached);

        KakaoDirectionsResponseDto.Route result = cachedDirectionsService.getRoute(origin(), destination());

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(delegate);
    }

    @Test
    void 캐시에_값이_없으면_카카오를_호출하고_결과를_저장한다() {
        KakaoDirectionsResponseDto.Route fresh = route();
        given(cache.get(anyString(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"))).willReturn(null);
        given(delegate.getRoute(any(), any())).willReturn(fresh);

        KakaoDirectionsResponseDto.Route result = cachedDirectionsService.getRoute(origin(), destination());

        assertThat(result).isSameAs(fresh);
        verify(cache).put("kakao:route:37.4979:127.027:37.5013:127.1027", fresh, TTL, "route");
    }

    @Test
    void 소수점_자릿수가_달라도_같은_좌표면_같은_키를_쓴다() {
        // 지오코딩 경유 좌표는 scale 이 가변이고 DB 경유 좌표는 scale 8 이다. 정규화가 없으면 서로의 캐시를 못 쓴다.
        given(cache.get(anyString(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"))).willReturn(null);
        given(delegate.getRoute(any(), any())).willReturn(route());

        GeoPoint paddedOrigin = new GeoPoint(new BigDecimal("37.49790000"), new BigDecimal("127.02700000"));
        GeoPoint paddedDestination = new GeoPoint(new BigDecimal("37.50130000"), new BigDecimal("127.10270000"));
        cachedDirectionsService.getRoute(paddedOrigin, paddedDestination);

        assertThat(capturedKey()).isEqualTo("kakao:route:37.4979:127.027:37.5013:127.1027");
    }

    @Test
    void 출발지와_도착지를_바꾸면_다른_키를_쓴다() {
        given(cache.get(anyString(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"))).willReturn(null);
        given(delegate.getRoute(any(), any())).willReturn(route());

        cachedDirectionsService.getRoute(destination(), origin());

        assertThat(capturedKey()).isEqualTo("kakao:route:37.5013:127.1027:37.4979:127.027");
    }

    @Test
    void 카카오가_실패하면_캐시에_저장하지_않는다() {
        given(cache.get(anyString(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"))).willReturn(null);
        given(delegate.getRoute(any(), any()))
                .willThrow(new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR));

        Throwable thrown = catchThrowable(() -> cachedDirectionsService.getRoute(origin(), destination()));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }

    @Test
    void TTL_이_0이면_캐시를_아예_건드리지_않는다() {
        ReflectionTestUtils.setField(cachedDirectionsService, "ttl", Duration.ZERO);
        KakaoDirectionsResponseDto.Route fresh = route();
        given(delegate.getRoute(any(), any())).willReturn(fresh);

        KakaoDirectionsResponseDto.Route result = cachedDirectionsService.getRoute(origin(), destination());

        assertThat(result).isSameAs(fresh);
        verifyNoInteractions(cache);
    }

    private String capturedKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).get(key.capture(), eq(KakaoDirectionsResponseDto.Route.class), eq("route"));
        return key.getValue();
    }

    private GeoPoint origin() {
        return new GeoPoint(new BigDecimal("37.4979"), new BigDecimal("127.0270"));
    }

    private GeoPoint destination() {
        return new GeoPoint(new BigDecimal("37.5013"), new BigDecimal("127.1027"));
    }

    private KakaoDirectionsResponseDto.Route route() {
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(1200, 900),
                new KakaoDirectionsResponseDto.Leg[0]);
    }
}
