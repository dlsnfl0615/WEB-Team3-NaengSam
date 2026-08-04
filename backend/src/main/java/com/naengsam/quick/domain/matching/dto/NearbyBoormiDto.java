package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.service.MatchingService;
import java.util.UUID;

public record NearbyBoormiDto(UUID boormiId, GeoPoint location, double distanceMeters) {

    public static NearbyBoormiDto from(MatchingService.WaitingBoormi boormi, double distanceMeters) {
        return new NearbyBoormiDto(boormi.boormiId(), boormi.location(), distanceMeters);
    }
}
