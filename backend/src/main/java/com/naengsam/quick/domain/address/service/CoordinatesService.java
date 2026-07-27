package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CoordinatesService {

    private static final String restApiKey = System.getenv("KAKAO_REST_API_KEY");

    private final RestClient restClient = RestClient.builder().build();

    public CoordinatesResponseDto getCoordinates(String roadAddress) {

        URI uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                .queryParam("query", roadAddress)
                .build()
                .toUri();

        return restClient.get()
                .uri(uri)
                .header("Authorization", "KakaoAK " + restApiKey)
                .retrieve()
                .body(CoordinatesResponseDto.class);
    }
}
