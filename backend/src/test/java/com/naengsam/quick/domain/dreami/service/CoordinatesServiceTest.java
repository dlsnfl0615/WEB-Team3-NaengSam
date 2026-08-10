package com.naengsam.quick.domain.dreami.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class CoordinatesServiceTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CoordinatesService serviceReturning(CoordinatesResponseDto response) {
        CoordinatesService coordinatesService = new CoordinatesService();

        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenReturn(uriSpec);
        when(uriSpec.header(any(), any())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(CoordinatesResponseDto.class)).thenReturn(response);

        ReflectionTestUtils.setField(coordinatesService, "restClient", restClient);
        return coordinatesService;
    }

    @Test
    void 도로명주소로_좌표를_조회한다() {
        // restApiKey는 static final이라 인스턴스 필드처럼 reflection으로 바꿀 수 없고, 아래 mock이
        // header(any(), any())로 값과 무관하게 매칭하므로 실제 키 값은 이 테스트에 영향을 주지 않는다.
        CoordinatesResponseDto.RoadAddress roadAddress = new CoordinatesResponseDto.RoadAddress(
                "서울 강남구 테헤란로 1", "서울", "강남구", null,
                "테헤란로", "1", null, null, "06134",
                "127.0276", "37.4979"
        );
        CoordinatesResponseDto expected = new CoordinatesResponseDto(
                List.of(new CoordinatesResponseDto.Document(roadAddress))
        );
        CoordinatesService coordinatesService = serviceReturning(expected);

        CoordinatesResponseDto result = coordinatesService.getCoordinates("서울시 강남구");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void 검색_결과가_없으면_EXTERNAL_SERVICE_ERROR_예외() {
        CoordinatesResponseDto empty = new CoordinatesResponseDto(List.of());
        CoordinatesService coordinatesService = serviceReturning(empty);

        Throwable thrown = catchThrowable(() -> coordinatesService.getCoordinates("존재하지 않는 주소"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    void 검색_결과에_도로명주소가_없으면_EXTERNAL_SERVICE_ERROR_예외() {
        CoordinatesResponseDto noRoadAddress = new CoordinatesResponseDto(
                List.of(new CoordinatesResponseDto.Document(null))
        );
        CoordinatesService coordinatesService = serviceReturning(noRoadAddress);

        Throwable thrown = catchThrowable(() -> coordinatesService.getCoordinates("지번만 있는 주소"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
