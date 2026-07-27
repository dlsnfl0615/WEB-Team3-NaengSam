package com.naengsam.quick.domain.dreami.dto;

import lombok.Builder;

@Builder
public record Addresses(
        String originAddressLine1, // 기본주소
        String originAddressLine2, // 상세주소
        String originLatitude,
        String originLongitude,
        String originAlias,

        String destinationAddressLine1,
        String destinationAddressLine2,
        String destinationLatitude,
        String destinationLongitude,
        String destinationAlias
) {
}
