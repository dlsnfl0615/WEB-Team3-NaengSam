package com.naengsam.quick.domain.address.dto;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * 주문(Orders) 에 반영할 출발지/도착지 배송지 정보를 담는 값 객체. 필드가 많아 매개변수로 나열하는 대신 이 레코드로 묶어서 전달한다.
 */
@Builder
public record Addresses(
        String originAddressLine1, // 기본주소
        String originAddressLine2, // 상세주소
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        String originAlias,

        String destinationAddressLine1,
        String destinationAddressLine2,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        String destinationAlias
) {
}
