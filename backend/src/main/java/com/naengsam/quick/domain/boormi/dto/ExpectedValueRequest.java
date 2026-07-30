package com.naengsam.quick.domain.boormi.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExpectedValueRequest(
        @NotBlank
        String originAddressLine1,

        @NotBlank
        String destinationAddressLine1,

        @NotNull
        ItemCd itemCd
){
}
