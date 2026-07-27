package com.naengsam.quick.domain.address.dto;

public record AddressRequestDto(
        String origin,
        String originDetail,
        String destination,
        String destinationDetail
) {
}
