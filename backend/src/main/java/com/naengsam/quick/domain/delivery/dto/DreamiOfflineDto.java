package com.naengsam.quick.domain.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 드리미 위치가 끊겼다고 알리는 SSE payload. 부르미(delivery_dreami_offline)와 드리미 본인
 * (delivery_dreami_offline_self)이 같은 판정 결과를 받으므로 payload도 하나를 공유한다.
 *
 * <p>마지막 수신 '시각'이 아니라 '경과 초'를 보낸다. 시각을 그대로 보내면 클라이언트 시계가 서버와
 * 어긋난 만큼 화면의 "마지막 확인 N분 전"이 틀리기 때문이다. 클라이언트는 이 경과 초를 자기 시계로
 * 역산해 기준점을 잡으므로 시계 오차의 영향을 받지 않는다.
 */
public record DreamiOfflineDto(
        @Schema(description = "주문 ID", example = "018f1c2e-8a4b-7c3d-9e0f-1a2b3c4d5e6f")
        UUID orderId,

        @Schema(description = "마지막으로 위치를 받은 뒤 흐른 시간(초)", example = "32")
        long secondsSinceLastLocation
) {
}
