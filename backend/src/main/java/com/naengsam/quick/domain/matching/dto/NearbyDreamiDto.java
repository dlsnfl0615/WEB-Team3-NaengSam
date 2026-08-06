package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.model.WaitingDreami;
import java.util.UUID;

public record NearbyDreamiDto(UUID dreamiId, GeoPoint location, double distanceMeters) {

    public static NearbyDreamiDto from(WaitingDreami dreami, double distanceMeters) {
        return new NearbyDreamiDto(dreami.dreamiId(), dreami.location(), distanceMeters);
    }
}
