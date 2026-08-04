package com.naengsam.quick.domain.matching.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record NearbyBoormiRequest(
        @NotNull BigDecimal lat,
        @NotNull BigDecimal lng,
        @NotNull @Positive Double radius,
        @NotNull @Positive Integer count // 최대 10개
) {
}
