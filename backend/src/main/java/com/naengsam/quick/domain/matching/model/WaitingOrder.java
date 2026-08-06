package com.naengsam.quick.domain.matching.model;

import java.util.UUID;

import com.naengsam.quick.domain.matching.dto.GeoPoint;

/**
 * 대기 중인 주문(매칭 시작 후 아직 확정되지 않은 주문). 별도 등록 큐 없이 {@link OrderOfferGroup}에서 그대로 도출되는 값이라 불변으로 둔다.
 */
public record WaitingOrder(UUID orderId, GeoPoint location) {
}
