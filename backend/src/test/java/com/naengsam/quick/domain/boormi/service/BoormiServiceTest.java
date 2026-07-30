package com.naengsam.quick.domain.boormi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.domain.address.service.KakaoDirectionsService;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 예상 견적(가격/시간/거리) 계산 로직 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class BoormiServiceTest {

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private CoordinatesService coordinatesService;

    @Mock
    private KakaoDirectionsService kakaoDirectionsService;

    @InjectMocks
    private BoormiService boormiService;

    private static ExpectedValueRequest request(ItemCd itemCd) {
        return new ExpectedValueRequest("서울시 강남구", "서울시 서초구", itemCd);
    }

    private static CoordinatesResponseDto coordinates() {
        CoordinatesResponseDto.RoadAddress roadAddress =
                new CoordinatesResponseDto.RoadAddress(
                        null, null, null, null, null, null, null, null, null,
                        "127.123456", "37.123456");
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(roadAddress)));
    }

    @Test
    void 문서_5km면_기본요금과_거리요금을_합산한다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedValue()).isEqualTo(10100);
        assertThat(result.expectedTime()).isEqualTo(15);
        assertThat(result.expectedDistance()).isEqualTo(5000);
    }

    @Test
    void PACKAGE는_배율15이_곱해진다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.PACKAGE));

        assertThat(result.expectedValue()).isEqualTo(15150);
    }

    @Test
    void ETA는_초를_분으로_올림한다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 901));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedTime()).isEqualTo(16);
    }

    @Test
    void 좌표변환_결과가_비면_EXTERNAL_SERVICE_ERROR() {
        given(coordinatesService.getCoordinates(anyString()))
                .willReturn(new CoordinatesResponseDto(List.of()));

        Throwable thrown = catchThrowable(() -> boormiService.expectedValue(request(ItemCd.DOCUMENT)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
