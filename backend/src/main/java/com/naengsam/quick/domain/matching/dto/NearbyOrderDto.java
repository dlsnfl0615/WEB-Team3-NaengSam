package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.model.WaitingOrder;
import java.util.UUID;

public record NearbyOrderDto(UUID orderId, GeoPoint location, double distanceMeters) {

    public static NearbyOrderDto from(WaitingOrder order, double distanceMeters) {
        return new NearbyOrderDto(order.orderId(), order.location(), distanceMeters);
    }
}
