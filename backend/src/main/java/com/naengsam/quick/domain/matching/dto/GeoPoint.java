package com.naengsam.quick.domain.matching.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record GeoPoint(@NotNull BigDecimal latitude, @NotNull BigDecimal longitude) {
}
