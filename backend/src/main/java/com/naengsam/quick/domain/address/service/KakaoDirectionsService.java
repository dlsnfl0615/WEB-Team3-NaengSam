package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.exception.AddressErrorCode;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.global.code.BaseErrorCode;
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
 * 카카오맵 도보 길찾기 API로 출발지→도착지의 실제 보행 거리(m)와 소요시간(s)을 구한다. {@code kakao.enabled} 가 없거나 true 일 때 활성화된다(KAKAO_REST_API_KEY
 * 필요).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "true", matchIfMissing = true)
public class KakaoDirectionsService implements DirectionsService {

    private static final String restApiKey = System.getenv("KAKAO_REST_API_KEY");
    // 카카오는 같은 국내망이라 핸드셰이크는 수십 ms, 정상 응답은 100~200ms 다. 아래 값도 정상 대비 10배 이상 여유다.
    // 길게 잡으면 카카오가 느려질 때 이미 이탈한 요청이 서버 자원을 계속 붙들고 있게 된다(#437).
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient = buildRestClient();

    /**
     * 어느 길찾기 구현이 떴는지 기동 로그로 남긴다. {@code kakao.enabled} 는 오타로 값이 사라져도 기본값 true 가 먹어 이 빈이 조용히 올라오므로, 배포 환경이 실 API 를 쓰고
     * 있는지 확인할 방법이 로그밖에 없다(#437).
     */
    @PostConstruct
    void init() {
        if (restApiKey == null || restApiKey.isBlank()) {
            // 키 없이 뜨면 모든 길찾기가 401 로 실패하므로, 차라리 기동을 실패시켜 배포에서 잡는다.
            throw new IllegalStateException("KAKAO_REST_API_KEY 가 없어 카카오 도보 길찾기를 초기화할 수 없습니다. "
                    + "크레덴셜 없이 띄우려면 kakao.enabled=false 로 두세요.");
        }
        log.info("도보 길찾기 = 카카오 실 API (connect {}s / read {}s)",
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
     * 출발지/도착지 좌표로 도보 경로를 조회해 요약(총 거리·소요시간)과 실제 이동경로 좌표가 담긴 Route 를 반환한다.
     */
    @Override
    public KakaoDirectionsResponseDto.Route getRoute(GeoPoint origin, GeoPoint destination) {

        URI uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/routing/walk")
                .queryParam("start_x", origin.longitude())
                .queryParam("start_y", origin.latitude())
                .queryParam("end_x", destination.longitude())
                .queryParam("end_y", destination.latitude())
                .build()
                .toUri();

        KakaoDirectionsResponseDto response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(KakaoDirectionsResponseDto.class);
        } catch (ResourceAccessException e) {
            log.warn("카카오 도보 길찾기 API 응답 지연", e);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);
        } catch (RestClientException e) {
            log.warn("카카오 도보 길찾기 API 호출 실패", e);
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        if (response == null) {
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        if (!"OK".equals(response.status())) {
            throw new BusinessException(toErrorCode(response.status()));
        }

        if (response.route() == null || response.route().properties() == null) {
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return response.route();
    }

    private BaseErrorCode toErrorCode(String status) {
        if (status == null) {
            log.warn("카카오 도보 길찾기 API가 상태코드 없이 응답함");
            return GeneralErrorCode.EXTERNAL_SERVICE_ERROR;
        }

        return switch (status) {
            case "SAME_POINT" -> AddressErrorCode.SAME_POINT;
            case "START_LINK_NOT_FOUND" -> AddressErrorCode.START_LINK_NOT_FOUND;
            case "END_LINK_NOT_FOUND" -> AddressErrorCode.END_LINK_NOT_FOUND;
            case "TOO_MANY_SEARCH_LINK" -> AddressErrorCode.TOO_MANY_SEARCH_LINK;
            case "TOO_FAR_AWAY" -> AddressErrorCode.TOO_FAR_AWAY;
            case "ROUTE_RESULT_NOT_FOUND" -> AddressErrorCode.ROUTE_RESULT_NOT_FOUND;
            default -> {
                log.warn("카카오 도보 길찾기 API가 알 수 없는 상태코드로 응답함: {}", status);
                yield GeneralErrorCode.EXTERNAL_SERVICE_ERROR;
            }
        };
    }
}
