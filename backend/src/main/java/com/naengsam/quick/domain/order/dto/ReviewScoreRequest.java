package com.naengsam.quick.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewScoreRequest(
        @Schema(description = "별점(1~5)", example = "5")
        @NotNull @Min(1) @Max(5) Integer score
) {
}
