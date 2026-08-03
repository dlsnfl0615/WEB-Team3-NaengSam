package com.naengsam.quick.domain.dreami.dto;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import java.math.BigDecimal;

public record DreamiProfileDto(
        String name,
        BigDecimal dreamiAvgScore,
        long rejectCount
) {
    public static DreamiProfileDto from(Dreami dreami, String name, long rejectCount) {
        return new DreamiProfileDto(name, dreami.getDreamiAvgScore(), rejectCount);
    }
}
