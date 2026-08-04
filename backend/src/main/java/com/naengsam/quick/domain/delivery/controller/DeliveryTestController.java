package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.global.session.PublicApi;
import com.naengsam.quick.global.sse.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 배달 시나리오를 눈으로 확인하기 위한 dev 전용 시딩 API. 매칭 도메인이 호출하는 진입점(startDelivery)이 컨트롤러에 노출돼 있지 않아,
 * 저장된 배달이 없으면 모든 배달 엔드포인트가 DELIVERY_NOT_FOUND를 낸다. 이 컨트롤러는 PICKUP_NORMAL 상태의 배달 한 건을
 * 즉석에서 만들어 시각 테스트 콘솔(/delivery-test.html)이 시나리오를 재생할 수 있게 한다.
 * <p>{@code @Profile("local")}로 게이트되어 운영 프로필에서는 빈으로 등록되지 않는다.
 */
@Slf4j
//@Profile("local")
@RestController
@RequestMapping("/api/v1/delivery/test")
@Tag(name = "배달테스트컨트롤러(dev)", description = "시각 테스트용 배달 시딩 API. local 프로필에서만 활성화된다.")
@RequiredArgsConstructor
@PublicApi   // 테스트 콘솔 전용 — 로그인 없이 seed/SSE 구독
public class DeliveryTestController {

    private final DeliveryService deliveryService;
    private final SseService sseService;
    private final OrderService orderService;

    /**
     * PICKUP_NORMAL 상태의 배달 한 건을 새로 만들어 저장하고, 생성한 식별자들을 돌려준다.
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

    /**
     * 매칭이 아직 구현되지 않은 상태에서 배달 플로우를 프론트에서 확인하기 위한 dev 전용 진입점.
     * boormiId/dreamiId를 받아 더미 주문 한 건을 DB에 영구 저장(매칭 확정 → IN_PROGRESS)한 뒤,
     * 그 주문을 기준으로 곧바로 {@link DeliveryService#startDelivery}를 호출해 배달(PICKUP_NORMAL)을 시작한다.
     */
    @Operation(summary = "주문 생성 + 배달 시작(dev)",
            description = "boormiId/dreamiId로 더미 주문을 DB에 저장하고, 해당 주문으로 배달을 시작(PICKUP_NORMAL)한 뒤 식별자를 반환한다.")
    @PostMapping("/order-and-start")
    public SeedResponse orderAndStart(@RequestParam UUID boormiId, @RequestParam UUID dreamiId) {
        UUID orderId = UUID.randomUUID();

        Orders order = Orders.create(orderId, boormiId, "테스트 물품", ItemCd.DOCUMENT,
                null, 5000L, 30, "테스트 배달 요청", null, dummyAddresses());
        order.assignDreamiTest(dreamiId);
        orderService.createOrders(order);

        deliveryService.startDelivery(orderId, dreamiId, boormiId);
        log.debug("[dev-order-and-start] 주문 저장 후 배달 시작 orderId={} boormiId={} dreamiId={}",
                orderId, boormiId, dreamiId);
        return new SeedResponse(orderId, dreamiId, boormiId);
    }

    private static Addresses dummyAddresses() {
        return Addresses.builder()
                .originAddressLine1("서울시 강남구 테헤란로 1")
                .originAddressLine2("101호")
                .originLatitude(new BigDecimal("37.49794000"))
                .originLongitude(new BigDecimal("127.02758000"))
                .originAlias("출발지")
                .destinationAddressLine1("서울시 송파구 올림픽로 300")
                .destinationAddressLine2("202호")
                .destinationLatitude(new BigDecimal("37.51512000"))
                .destinationLongitude(new BigDecimal("127.10425000"))
                .destinationAlias("도착지")
                .build();
    }

    /**
     * 로그인 없이 임의 UUID로 SSE를 구독하는 dev 전용 엔드포인트. 실제 구독(/api/v1/sse/subscribe)은 로그인 필수라
     * seed가 만든 랜덤 boormiId/dreamiId로는 구독할 수 없어, 테스트 콘솔이 이 경로로 구독한다. 실제 SSE 경로를 그대로 태운다.
     */
    @Operation(summary = "SSE 구독(dev)", description = "임의 userId(boormiId/dreamiId)로 로그인 없이 SSE를 구독한다. 테스트 콘솔 전용.")
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter devSubscribe(@PathVariable UUID userId) {
        return sseService.subscribe(userId);
    }
}
