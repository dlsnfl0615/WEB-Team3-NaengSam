package com.naengsam.quick.domain.matching.dto;

import com.naengsam.quick.domain.matching.policy.scope.OfferPolicySnapshot;
import java.time.LocalDateTime;

/**
 * 오퍼에 저장된 {@link OfferPolicySnapshot}을 그대로 클라이언트에 전달하기 위한 DTO. 클라이언트가
 * matching.offer-scopes 설정을 다시 해석하지 않고, 이 오퍼가 생성된 시점에 실제로 적용됐던 탐색 범위를 그대로
 * 받는다.
 */
public record OfferPolicyDto(
        long scopeKeySeconds,           // 적용된 scope 임계값의 minOrderWait(초)
        LocalDateTime evaluatedAt,      // 이 판단이 이뤄진 배치 평가 시각
        long orderWaitingSeconds,       // 판단 시점의 주문 대기시간(초)
        double pickupDistanceMeters,    // 실제 부르미-드리미 픽업거리
        long maxPickupDistanceMeters    // 그 시점에 적용된 scope가 허용한 최대 픽업거리
) {

    /**
     * snapshot이 없으면(예: 이 기능 이전에 만들어졌거나 스냅샷 없이 생성된 테스트용 오퍼) null을 반환한다.
     */
    public static OfferPolicyDto from(OfferPolicySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new OfferPolicyDto(
                snapshot.scopeKey().toSeconds(),
                snapshot.evaluatedAt(),
                snapshot.orderWaitingSeconds(),
                snapshot.pickupDistanceMeters(),
                snapshot.maxPickupDistanceMeters());
    }
}
