package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부르미 응답 timeout으로 인메모리 매칭 엔진이 확정 대기 오퍼를 회수할 때, 그 결과를 DB 주문에도 반영한다.
 * {@code MatchingService}가 {@link OrderRepository}를 직접 다루지 않도록 이 서비스로 분리했다.
 *
 * <p>{@link OrderRepository#findByOrderId(UUID)}의 비관적 쓰기 락을 그대로 활용해, 드리미 수락(
 * {@code DreamiService.acceptOffer})·부르미 확정/거절(
 * {@code BoormiService.confirmDreami}/{@code rejectDreami})과 동일한 주문 잠금 경로를 타게 한다.
 * timeout과 부르미 확정이 동시에 들어와도 먼저 락을 잡은 트랜잭션이 승자가 되고, 나머지는 갱신된
 * 최신 상태(이미 PENDING_BOORMI_CONFIRMATION이 아니거나 dreamiId가 다름)를 다시 읽어 조용히 실패한다.
 */
@Service
@RequiredArgsConstructor
public class BoormiOfferExpirationService {

    private final OrderRepository orderRepository;

    /**
     * 부르미 응답 timeout을 DB에 반영한다. 그 사이 부르미가 이미 확정/거절해 주문이 더 이상 이 드리미의
     * PENDING_BOORMI_CONFIRMATION이 아니게 됐으면 아무것도 바꾸지 않고 false를 반환한다 — 호출자는 이 경우
     * 인메모리 상태(MatchOffer 등)도 건드리지 않아야 한다.
     *
     * @return 실제로 MATCHING으로 되돌렸으면 true
     */
    @Transactional
    public boolean expire(UUID orderId, UUID dreamiId) {
        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getOrderCd() != OrderCd.PENDING_BOORMI_CONFIRMATION) {
            return false;
        }
        if (!dreamiId.equals(order.getDreamiId())) {
            return false;
        }

        order.rejectDreami();
        return true;
    }
}
