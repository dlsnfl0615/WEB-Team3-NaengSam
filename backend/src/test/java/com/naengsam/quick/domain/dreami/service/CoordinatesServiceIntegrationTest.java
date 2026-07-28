package com.naengsam.quick.domain.dreami.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest
class CoordinatesServiceIntegrationTest {

    @Autowired
    private CoordinatesService coordinatesService;

    @Test
    void 실제_카카오_API로_도로명주소를_좌표로_변환한다() {
        CoordinatesResponseDto response = coordinatesService.getCoordinates("서울특별시 강남구 테헤란로 427");

        assertThat(response.documents()).isNotEmpty();

        CoordinatesResponseDto.RoadAddress roadAddress = response.documents().getFirst().roadAddress();
        assertThat(roadAddress.y()).isNotBlank(); // 위도
        assertThat(roadAddress.x()).isNotBlank(); // 경도
    }
}
