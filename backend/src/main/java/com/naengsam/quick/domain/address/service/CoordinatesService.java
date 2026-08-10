package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class CoordinatesService {

    private static final String restApiKey = System.getenv("KAKAO_REST_API_KEY");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient = buildRestClient();

    private static RestClient buildRestClient() {
        // 연결 자체가 안 되는 상황을 막기 위해
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT); // api 요청 타임아웃

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 카카오 로컬 API로 도로명주소를 위도/경도로 변환한다. 검색 결과가 없거나 도로명주소가 없는 경우도
     * 여기서 걸러내므로, 호출한 곳은 {@code documents().getFirst().roadAddress()}를 바로 써도 안전하다.
     */
    public CoordinatesResponseDto getCoordinates(String roadAddress) {

        URI uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                .queryParam("query", roadAddress)
                .build()
                .toUri();

        CoordinatesResponseDto response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(CoordinatesResponseDto.class);
        } catch (ResourceAccessException e) {
            log.warn("카카오 좌표 변환 API 응답 지연: {}", roadAddress, e);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);
        } catch (RestClientException e) {
            log.warn("카카오 좌표 변환 API 호출 실패: {}", roadAddress, e);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        List<CoordinatesResponseDto.Document> documents = response.documents();
        if (documents.isEmpty() || documents.getFirst().roadAddress() == null) {
            log.warn("카카오 좌표 변환 결과 없음(검색 결과 없음 또는 도로명주소 없음): {}", roadAddress);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return response;
    }
}
