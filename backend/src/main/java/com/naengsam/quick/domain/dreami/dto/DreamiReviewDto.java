package com.naengsam.quick.domain.dreami.dto;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import java.time.LocalDateTime;
import java.util.UUID;

public record DreamiReviewDto(UUID dreamiId, String idCardImageUrl, String criminalRecordImageUrl,
                               LocalDateTime requestDtm) {

    public static DreamiReviewDto of(Dreami dreami, String idCardImageUrl, String criminalRecordImageUrl) {
        return new DreamiReviewDto(dreami.getDreamiId(), idCardImageUrl, criminalRecordImageUrl,
                dreami.getRequestDtm());
    }
}
