package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 배달 시나리오를 눈으로 확인하기 위한 dev 전용 시딩 API. 매칭 도메인이 호출하는 진입점(startDelivery)이 컨트롤러에 노출돼 있지 않아,
 * store가 비어 있으면 모든 배달 엔드포인트가 DELIVERY_NOT_FOUND를 낸다. 이 컨트롤러는 PICKUP_NORMAL 상태의 배달 한 건을
 * 즉석에서 만들어 시각 테스트 콘솔(/delivery-test.html)이 시나리오를 재생할 수 있게 한다.
 * <p>{@code @Profile("local")}로 게이트되어 운영 프로필에서는 빈으로 등록되지 않는다.
 */
@Slf4j
//@Profile("local")
@RestController
@RequestMapping("/api/v1/delivery/test")
@Tag(name = "배달테스트컨트롤러(dev)", description = "시각 테스트용 배달 시딩 API. local 프로필에서만 활성화된다.")
@RequiredArgsConstructor
public class DeliveryTestController {

    private final DeliveryService deliveryService;

    /**
     * PICKUP_NORMAL 상태의 배달 한 건을 새로 만들어 store에 등록하고, 생성한 식별자들을 돌려준다.
     */
    @Operation(summary = "배달 시딩(dev)",
            description = "orderId/dreamiId/boormiId로 배달을 시작(PICKUP_NORMAL)하고 식별자를 반환한다. "
                    + "orderId를 넘기면 해당 주문 식별자로 등록하고, 생략하면 새로 발급한다.")
    @PostMapping("/seed")
    public SeedResponse seed(@RequestParam(required = false) UUID orderId) {
        UUID resolvedOrderId = orderId != null ? orderId : UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        deliveryService.startDelivery(resolvedOrderId, dreamiId, boormiId);
        log.debug("[dev-seed] 배달 시딩 orderId={}", resolvedOrderId);
        return new SeedResponse(resolvedOrderId, dreamiId, boormiId);
    }

    public record SeedResponse(UUID orderId, UUID dreamiId, UUID boormiId) {
    }
}
