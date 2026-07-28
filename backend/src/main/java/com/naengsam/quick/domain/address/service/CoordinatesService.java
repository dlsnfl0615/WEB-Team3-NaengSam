package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class CoordinatesService {

    private static final String restApiKey = System.getenv("KAKAO_REST_API_KEY");

    private final RestClient restClient = RestClient.builder().build();

    /**
     * 카카오 로컬 API로 도로명주소를 위도/경도로 변환한다.
     */
    public CoordinatesResponseDto getCoordinates(String roadAddress) {

        URI uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                .queryParam("query", roadAddress)
                .build()
                .toUri();

        try {
            return restClient.get()
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
    }
}
