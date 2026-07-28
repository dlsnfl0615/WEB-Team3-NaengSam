package com.naengsam.quick.domain.address.dto;

import java.util.UUID;

public record AddressRequestDto(
        String addressAlias,
        String addressLine1,
        String addressLine2,
        UUID boormiId
) {
}
