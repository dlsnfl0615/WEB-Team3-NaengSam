package com.naengsam.quick.domain.delivery.dto;

import java.math.BigDecimal;

/**
 * 드리미 위치 갱신 요청. 좌표 정밀도는 서비스에서 소수점 8자리로 정규화한다.
 */
public record DreamiLocationRequest(
        BigDecimal latitude,
        BigDecimal longitude
) {
}
