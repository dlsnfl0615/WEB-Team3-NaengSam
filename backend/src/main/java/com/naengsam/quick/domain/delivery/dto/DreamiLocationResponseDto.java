package com.naengsam.quick.domain.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 드리미 위치 전송(updateDreamiLocation)의 응답. 드리미의 첫 위치가 들어오면 서버가 계산해 저장한
 * '드리미→픽업지' 경로와 배송완료예상시간을 함께 돌려준다(아직 계산 전이면 빈 목록/null).
 * 프론트가 이 응답만으로 화면을 갱신할 수 있어 별도 재조회가 필요 없다.
 */
public record DreamiLocationResponseDto(
        @Schema(description = "드리미 위치→픽업지 카카오 도보 경로 좌표 목록(픽업 전 지도 폴리라인용). 아직 계산 전이면 빈 배열")
        List<RoutePointDto> deliveryRoutePath,

        @Schema(description = "배송완료예상시간(드리미→픽업지 소요 + 주문 delivery_eta). 아직 계산 전이면 null")
        LocalDateTime estimatedCompletionTime
) {
}
