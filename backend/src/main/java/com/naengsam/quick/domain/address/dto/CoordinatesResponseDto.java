package com.naengsam.quick.domain.address.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true) // 정의하지 않은 메타 데이터나 다른 필드는 무시
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoordinatesResponseDto(
        List<Document> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Document(
            RoadAddress roadAddress
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RoadAddress(
            String addressName,
            String region1depthName,
            String region2depthName,
            String region3depthName,
            String roadName,
            String mainBuildingNo,
            String subBuildingNo,
            String buildingName,
            String zoneNo,
            String x, // 경도
            String y  // 위도
    ) {
    }
}