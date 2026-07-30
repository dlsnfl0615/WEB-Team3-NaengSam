package com.naengsam.quick.domain.delivery.dto;

import java.util.UUID;

public record Order(UUID orderId, UUID boormiId, GeoPoint destination) {
}
