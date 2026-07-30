package com.naengsam.quick.domain.matching.dto;

import java.util.UUID;

public record Order(UUID orderId, UUID boormiId, GeoPoint destination) {
}
