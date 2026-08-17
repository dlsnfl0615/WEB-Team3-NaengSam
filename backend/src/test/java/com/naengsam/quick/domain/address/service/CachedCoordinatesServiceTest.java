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

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 지오코딩 캐시 데코레이터가 히트 시 카카오를 건너뛰는지, 실패 응답을 캐싱하지 않는지, TTL 킬 스위치가 먹는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CachedCoordinatesServiceTest {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String ADDRESS = "서울특별시 강남구 테헤란로 427";

    @Mock
    private KakaoCoordinatesService delegate;
    @Mock
    private KakaoResponseCache cache;
    @InjectMocks
    private CachedCoordinatesService cachedCoordinatesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cachedCoordinatesService, "ttl", TTL);
    }

    @Test
    void 캐시에_값이_있으면_카카오를_호출하지_않는다() {
        CoordinatesResponseDto cached = coordinates();
        given(cache.get("kakao:geo:" + ADDRESS, CoordinatesResponseDto.class, "geocode")).willReturn(cached);

        CoordinatesResponseDto result = cachedCoordinatesService.getCoordinates(ADDRESS);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(delegate);
    }

    @Test
    void 캐시에_값이_없으면_카카오를_호출하고_결과를_저장한다() {
        CoordinatesResponseDto fresh = coordinates();
        given(cache.get(anyString(), eq(CoordinatesResponseDto.class), eq("geocode"))).willReturn(null);
        given(delegate.getCoordinates(ADDRESS)).willReturn(fresh);

        CoordinatesResponseDto result = cachedCoordinatesService.getCoordinates(ADDRESS);

        assertThat(result).isSameAs(fresh);
        verify(cache).put("kakao:geo:" + ADDRESS, fresh, TTL, "geocode");
    }

    @Test
    void 앞뒤_공백이_다른_같은_주소는_같은_키를_쓴다() {
        given(cache.get(anyString(), eq(CoordinatesResponseDto.class), eq("geocode"))).willReturn(null);
        given(delegate.getCoordinates(anyString())).willReturn(coordinates());

        cachedCoordinatesService.getCoordinates("  서울특별시   강남구  테헤란로 427 ");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).get(key.capture(), eq(CoordinatesResponseDto.class), eq("geocode"));
        assertThat(key.getValue()).isEqualTo("kakao:geo:" + ADDRESS);
    }

    @Test
    void 정규화한_주소가_아니라_원본_주소로_카카오를_호출한다() {
        given(cache.get(anyString(), eq(CoordinatesResponseDto.class), eq("geocode"))).willReturn(null);
        given(delegate.getCoordinates(anyString())).willReturn(coordinates());

        cachedCoordinatesService.getCoordinates("  서울특별시   강남구  테헤란로 427 ");

        verify(delegate).getCoordinates("  서울특별시   강남구  테헤란로 427 ");
    }

    @Test
    void 카카오가_실패하면_캐시에_저장하지_않는다() {
        given(cache.get(anyString(), eq(CoordinatesResponseDto.class), eq("geocode"))).willReturn(null);
        given(delegate.getCoordinates(ADDRESS))
                .willThrow(new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        Throwable thrown = catchThrowable(() -> cachedCoordinatesService.getCoordinates(ADDRESS));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }

    @Test
    void TTL_이_0이면_캐시를_아예_건드리지_않는다() {
        ReflectionTestUtils.setField(cachedCoordinatesService, "ttl", Duration.ZERO);
        CoordinatesResponseDto fresh = coordinates();
        given(delegate.getCoordinates(ADDRESS)).willReturn(fresh);

        CoordinatesResponseDto result = cachedCoordinatesService.getCoordinates(ADDRESS);

        assertThat(result).isSameAs(fresh);
        verifyNoInteractions(cache);
    }

    private CoordinatesResponseDto coordinates() {
        CoordinatesResponseDto.RoadAddress roadAddress = new CoordinatesResponseDto.RoadAddress(
                "서울 강남구 테헤란로 427", "서울", "강남구", "역삼동", "테헤란로",
                "427", null, "위워크타워", "06159", "127.027", "37.4987");
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(roadAddress)));
    }
}
