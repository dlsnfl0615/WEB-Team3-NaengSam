package com.naengsam.quick.domain.matching.service;

import java.util.UUID;

record BoormiOfferTimeout(UUID offerId, long executeAtMillis) implements MatchingTimeout {
}
