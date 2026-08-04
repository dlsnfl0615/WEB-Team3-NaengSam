package com.naengsam.quick.domain.dreami.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DreamiOnlineRequest(
        @NotNull
        BigDecimal latitude,

        @NotNull
        BigDecimal longitude
) {
}
