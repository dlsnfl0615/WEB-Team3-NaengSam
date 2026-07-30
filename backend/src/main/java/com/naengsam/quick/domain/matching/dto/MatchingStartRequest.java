package com.naengsam.quick.domain.matching.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MatchingStartRequest(
        @NotNull UUID boormiId,
        @NotNull GeoPoint destination
) {
}
