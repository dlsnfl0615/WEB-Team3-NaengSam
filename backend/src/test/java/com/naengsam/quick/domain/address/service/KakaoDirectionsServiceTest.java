package com.naengsam.quick.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.exception.AddressErrorCode;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/**
 * 카카오 도보 길찾기 응답 상태에 따른 경로 반환과 예외 매핑을 검증한다.
 */
class KakaoDirectionsServiceTest {

    private static final GeoPoint ORIGIN = new GeoPoint(new BigDecimal("37.4979"), new BigDecimal("127.0276"));
    private static final GeoPoint DESTINATION = new GeoPoint(new BigDecimal("37.5665"), new BigDecimal("126.9780"));

    @ParameterizedTest
    @MethodSource("knownErrorStatuses")
    void 카카오_오류상태는_대응하는_ADDRESS_예외(String status, AddressErrorCode expectedErrorCode) {
        KakaoDirectionsService service = serviceReturning(new KakaoDirectionsResponseDto(null, status));

        Throwable thrown = catchThrowable(() -> service.getRoute(ORIGIN, DESTINATION));

        assertThat(errorCodeOf(thrown)).isEqualTo(expectedErrorCode);
    }

    @Test
    void OK_응답이면_경로를_반환한다() {
        KakaoDirectionsResponseDto.Route route = route();
        KakaoDirectionsService service = serviceReturning(new KakaoDirectionsResponseDto(route, "OK"));

        KakaoDirectionsResponseDto.Route result = service.getRoute(ORIGIN, DESTINATION);

        assertThat(result).isSameAs(route);
    }

    @ParameterizedTest
    @MethodSource("invalidOkResponses")
    void OK_응답의_경로정보가_누락되면_EXTERNAL_SERVICE_ERROR(KakaoDirectionsResponseDto response) {
        KakaoDirectionsService service = serviceReturning(response);

        Throwable thrown = catchThrowable(() -> service.getRoute(ORIGIN, DESTINATION));

        assertThat(errorCodeOf(thrown)).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @ParameterizedTest
    @MethodSource("unknownStatuses")
    void 알수없는_상태이면_EXTERNAL_SERVICE_ERROR(String status) {
        KakaoDirectionsService service = serviceReturning(new KakaoDirectionsResponseDto(null, status));

        Throwable thrown = catchThrowable(() -> service.getRoute(ORIGIN, DESTINATION));

        assertThat(errorCodeOf(thrown)).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    void 응답이_없으면_EXTERNAL_SERVICE_ERROR() {
        KakaoDirectionsService service = serviceReturning(null);

        Throwable thrown = catchThrowable(() -> service.getRoute(ORIGIN, DESTINATION));

        assertThat(errorCodeOf(thrown)).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    private static Stream<Arguments> knownErrorStatuses() {
        return Stream.of(
                Arguments.of("SAME_POINT", AddressErrorCode.SAME_POINT),
                Arguments.of("START_LINK_NOT_FOUND", AddressErrorCode.START_LINK_NOT_FOUND),
                Arguments.of("END_LINK_NOT_FOUND", AddressErrorCode.END_LINK_NOT_FOUND),
                Arguments.of("TOO_MANY_SEARCH_LINK", AddressErrorCode.TOO_MANY_SEARCH_LINK),
                Arguments.of("TOO_FAR_AWAY", AddressErrorCode.TOO_FAR_AWAY),
                Arguments.of("ROUTE_RESULT_NOT_FOUND", AddressErrorCode.ROUTE_RESULT_NOT_FOUND)
        );
    }

    private static Stream<Arguments> invalidOkResponses() {
        return Stream.of(
                Arguments.of(new KakaoDirectionsResponseDto(null, "OK")),
                Arguments.of(new KakaoDirectionsResponseDto(
                        new KakaoDirectionsResponseDto.Route(null, new KakaoDirectionsResponseDto.Leg[0]), "OK"))
        );
    }

    private static Stream<Arguments> unknownStatuses() {
        return Stream.of(
                Arguments.of("UNKNOWN_STATUS"),
                Arguments.of((String) null)
        );
    }

    private static KakaoDirectionsResponseDto.Route route() {
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(5_000, 900),
                new KakaoDirectionsResponseDto.Leg[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KakaoDirectionsService serviceReturning(KakaoDirectionsResponseDto response) {
        KakaoDirectionsService service = new KakaoDirectionsService();
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        given(restClient.get()).willReturn(uriSpec);
        given(uriSpec.uri(any(URI.class))).willReturn(uriSpec);
        given(uriSpec.header(any(), any())).willReturn(uriSpec);
        given(uriSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(KakaoDirectionsResponseDto.class)).willReturn(response);
        ReflectionTestUtils.setField(service, "restClient", restClient);
        return service;
    }

    private static BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }
}
