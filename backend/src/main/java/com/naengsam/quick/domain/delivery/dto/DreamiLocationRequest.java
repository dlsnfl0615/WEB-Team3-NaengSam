package com.naengsam.quick.domain.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 드리미 위치 갱신 요청. 좌표 정밀도는 서비스에서 소수점 8자리로 정규화한다.
 * includeRoute=true면 응답에 '드리미→픽업지' 경로·배송완료예상시간을 함께 받는다(아직 값이 없는 드리미가 사용).
 * 값을 한 번 받은 뒤에는 false로 보내 좌표 배열을 매번 중복 수신하지 않게 한다(null이면 하위호환으로 포함).
 */
public record DreamiLocationRequest(
        BigDecimal latitude,
        BigDecimal longitude,

        @Schema(description = "true면 응답에 드리미→픽업지 경로·배송완료예상시간을 포함한다. 이미 받은 뒤에는 false. 생략 시 포함(하위호환).")
        Boolean includeRoute
) {
    /** includeRoute 없이 좌표만으로 만드는 편의 생성자(생략 시 경로 포함으로 동작). */
    public DreamiLocationRequest(BigDecimal latitude, BigDecimal longitude) {
        this(latitude, longitude, null);
    }
}
