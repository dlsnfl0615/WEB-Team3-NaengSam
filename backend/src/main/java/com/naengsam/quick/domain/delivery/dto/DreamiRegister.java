package com.naengsam.quick.domain.delivery.dto;

import java.util.UUID;

public record DreamiRegister(UUID dreamiId, GeoPoint location) implements Action {
}