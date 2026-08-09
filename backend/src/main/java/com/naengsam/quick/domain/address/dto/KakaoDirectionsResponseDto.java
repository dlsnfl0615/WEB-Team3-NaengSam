package com.naengsam.quick.domain.address.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 카카오맵 도보 길찾기 API 응답. 요금 계산에 필요한 총 거리/소요시간만 매핑하고 나머지는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoDirectionsResponseDto(
        Route route,
        String status
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            Properties properties, // 전체 경로 속성
            Leg[] legs // 경로 구간 목록
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(
            LegProperties properties, // 경로 구간 속성
            Step[] steps // 경로 구간 단계 목록
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegProperties(
            int distance, // 경로 구간 거리(단위: 미터(m))
            int time // 경로 구간 소요 시간(단위: 초(s))
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            StepProperties properties, // 경로 단계 속성
            Path path // 경로 단계 좌표 정보
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepProperties(
            int distance, // 경로 단계 거리(단위: 미터(m))
            String guidance, // 경로 단계 안내 문구
            int time, // 경로 단계 소요 시간(단위: 초(s))
            double x, // 경로 단계 시작점 X 좌표
            double y // 경로 단계 시작점 Y 좌표
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(
            // 경로 단계 좌표 목록, 내부 배열은 [x, y] 형식의 좌표 쌍
            // (예: [[127.02700693, 37.49864277], [127.02698289, 37.49863151]])
            double[][] points
    ) {
    }

    /**
     * 경로 요약 정보. totalDistance=미터, totalTime=초.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            int totalDistance,
            int totalTime
    ) {
    }
}
