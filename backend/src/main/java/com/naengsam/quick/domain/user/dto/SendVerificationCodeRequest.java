package com.naengsam.quick.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendVerificationCodeRequest(
        @NotBlank
        @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        String phoneNumber
) {
}
