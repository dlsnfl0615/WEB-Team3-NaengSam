package com.naengsam.quick.domain.dreami.dto;

public record PresignedUrlResponseDto(
        String idCardUrl,
        String idCardKey,
        String criminalRecordUrl,
        String criminalRecordKey
) {
}
