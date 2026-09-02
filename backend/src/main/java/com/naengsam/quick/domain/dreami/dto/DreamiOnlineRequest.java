package com.naengsam.quick.domain.dreami.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DreamiOnlineRequest(
        @NotNull // Jakarta Bean Validation: 값이 null이면 검증 실패 (컨트롤러의 @Valid가 실제로 검사를 실행함)
        BigDecimal latitude,

        @NotNull
        BigDecimal longitude
) {
}
