package com.naengsam.quick.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewContentRequest(
        @Schema(description = "리뷰 내용(최대 200자)", example = "친절하고 빨랐어요")
        @NotBlank @Size(max = 200) String content
) {
}
