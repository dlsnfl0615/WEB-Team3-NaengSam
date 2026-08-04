package com.naengsam.quick.domain.boormi.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmDreamiRequest(
        @NotNull
        UUID offerId
) {
}
