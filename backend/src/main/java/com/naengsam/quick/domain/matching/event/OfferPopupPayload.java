package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * 드리미에게 새 제안 팝업을 띄울 때 전달하는 payload. 프론트가 요금·픽업지·도착지 등을 바로 표시할 수 있도록 주문 요약 정보를 함께 담는다. 상세 정보는 매칭 시작 시점의 스냅샷이며 이후 변하지
 * 않는다.
 */
public record OfferPopupPayload(
        UUID offerId,
        UUID orderId,
        Long deliveryAmount,             // 요금
        String itemName,
        int deliveryEta,
        Long deliveryDistance,           // 출발지-도착지 예상 거리(m)
        BigDecimal originLatitude,       // 픽업지 위도
        BigDecimal originLongitude,      // 픽업지 경도
        String originAlias,              // 픽업지 별칭
        String originAddressLine1,       // 픽업지 기본주소
        BigDecimal destinationLatitude,  // 도착지 위도
        BigDecimal destinationLongitude, // 도착지 경도
        String destinationAlias,         // 도착지 별칭
        String destinationAddressLine1,  // 도착지 기본주소
        String imageKey,
        long ttlSeconds                  // 남은 응답 시간(초)
) {

    public static OfferPopupPayload from(MatchOffer offer, OrderSummaryDto summary, Duration ttl) {
        return new OfferPopupPayload(
                offer.offerId(),
                offer.orderId(),
                summary.deliveryAmount(),
                summary.itemName(),
                summary.deliveryEta(),
                summary.deliveryDistance(),
                summary.originLatitude(),
                summary.originLongitude(),
                summary.originAlias(),
                summary.originAddressLine1(),
                summary.destinationLatitude(),
                summary.destinationLongitude(),
                summary.destinationAlias(),
                summary.destinationAddressLine1(),
                summary.imageKey(),
                ttl.getSeconds());
    }
}
