package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.service.MatchingService;
import java.util.UUID;

public record NearbyOrderDto(UUID orderId, GeoPoint location, double distanceMeters) {

    public static NearbyOrderDto from(MatchingService.WaitingOrder order, double distanceMeters) {
        return new NearbyOrderDto(order.orderId(), order.location(), distanceMeters);
    }
}
