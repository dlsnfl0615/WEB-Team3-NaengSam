package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.user.dto.ActiveContext;
import com.naengsam.quick.domain.user.dto.ActiveRole;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 지금 무엇을 수행 중인지 판정하는 단일 진입점.
 *
 * <p>진행 상태는 두 곳에 나뉘어 있다. 주문·배달은 ORDERS에 영속되고, 드리미가 오퍼를 기다리는 "온라인 대기"는 매칭엔진의 인메모리 상태에만 있다. 역할 전환 차단, 로그인 후 화면 복귀,
 * 드리미 온라인 전환이 모두 같은 기준으로 판단해야 하므로 이 한 곳에서 둘을 합쳐 본다.
 */
@Component
@RequiredArgsConstructor
public class UserActivityResolver {

    /**
     * 진행 중으로 보는 주문 상태. {@code OrderRepository.countActiveOrders}가 제외하는 종료 상태(COMPLETED/CANCELLED/CLAIM_REVIEW)의 여집합과 같다.
     */
    private static final Set<OrderCd> ACTIVE_ORDER_CDS = EnumSet.of(
            OrderCd.MATCHING,
            OrderCd.PENDING_BOORMI_CONFIRMATION,
            OrderCd.IN_PROGRESS,
            OrderCd.WAITING_CONFIRMATION);

    private final OrderRepository orderRepository;
    private final MatchingService matchingService;

    /**
     * 사용자의 현재 수행 상태를 판정한다. 아무것도 수행 중이 아니면 {@link ActiveContext#idle()}을 반환한다.
     */
    @Transactional(readOnly = true)
    public ActiveContext resolve(UUID userId) {
        // 드리미를 먼저 본다. dreami_id 매치가 더 구체적인 신호이고, 부르미 확인 대기 중인 주문은
        // boormi_id/dreami_id 어느 쪽으로도 잡히므로 순서를 뒤집으면 드리미가 부르미로 오판된다.
        Orders dreamiOrder = orderRepository.findByDreamiIdAndOrderCdIn(userId, ACTIVE_ORDER_CDS)
                .orElse(null);
        if (dreamiOrder != null) {
            return ActiveContext.of(ActiveRole.DREAMI, dreamiOrder.getOrderId(), dreamiOrder.getOrderCd());
        }

        Orders boormiOrder = orderRepository
                .findFirstByBoormiIdAndOrderCdInOrderByDeliveryRequestDtmDesc(userId, ACTIVE_ORDER_CDS)
                .orElse(null);
        if (boormiOrder != null) {
            return ActiveContext.of(ActiveRole.BOORMI, boormiOrder.getOrderId(), boormiOrder.getOrderCd());
        }

        // 주문이 없어도 드리미가 오퍼를 기다리는 중일 수 있다. 이 상태만 DB에 흔적이 없다.
        if (matchingService.isDreamiWaiting(userId)) {
            return ActiveContext.dreamiWaiting();
        }

        return ActiveContext.idle();
    }
}
