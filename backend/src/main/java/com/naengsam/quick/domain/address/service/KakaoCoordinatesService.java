package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로컬 API 기반 좌표 변환기. {@code kakao.enabled} 가 없거나 true 일 때 활성화된다(KAKAO_REST_API_KEY 필요).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "true", matchIfMissing = true)
public class KakaoCoordinatesService implements CoordinatesService {

    private static final String restApiKey = System.getenv("KAKAO_REST_API_KEY");
    // 카카오는 같은 국내망이라 핸드셰이크는 수십 ms, 정상 응답은 100~200ms 다. 아래 값도 정상 대비 10배 이상 여유다.
    // 길게 잡으면 카카오가 느려질 때 이미 이탈한 요청이 서버 자원을 계속 붙들고 있게 된다(#437).
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient = buildRestClient();

    /**
     * 어느 좌표 변환 구현이 떴는지 기동 로그로 남긴다. {@code kakao.enabled} 는 오타로 값이 사라져도 기본값 true 가 먹어 이 빈이 조용히 올라오므로, 배포 환경이 실 API 를 쓰고
     * 있는지 확인할 방법이 로그밖에 없다(#437).
     */
    @PostConstruct
    void init() {
        if (restApiKey == null || restApiKey.isBlank()) {
            // 키 없이 뜨면 모든 주소 검색이 401 로 실패하므로, 차라리 기동을 실패시켜 배포에서 잡는다.
            throw new IllegalStateException("KAKAO_REST_API_KEY 가 없어 카카오 좌표 변환을 초기화할 수 없습니다. "
                    + "크레덴셜 없이 띄우려면 kakao.enabled=false 로 두세요.");
        }
        log.info("좌표 변환 = 카카오 실 API (connect {}s / read {}s)",
                CONNECT_TIMEOUT.toSeconds(), READ_TIMEOUT.toSeconds());
    }

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
     * 카카오 로컬 API로 도로명주소를 위도/경도로 변환한다.
     */
    @Override
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
