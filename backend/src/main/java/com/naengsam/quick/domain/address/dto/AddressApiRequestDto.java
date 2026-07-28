package com.naengsam.quick.domain.address.dto;

public record AddressApiRequestDto(
        String origin,
        String originDetail,
        String destination,
        String destinationDetail
) {
}
