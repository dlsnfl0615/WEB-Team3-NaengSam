package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.entity.Cancel;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.CancelRepository;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CancelRepository cancelRepository;

    @Transactional
    public Orders createOrders(Orders order) {
        return orderRepository.save(order);
    }

    /**
     * 해당 사용자가 부르미/드리미로 진행 중(완료·취소·클레임 제외)인 주문 수를 센다.
     */
    @Transactional(readOnly = true)
    public long countActiveOrders(UUID userId) {
        return orderRepository.countActiveOrders(userId);
    }

    /**
     * 주문을 조회한다. 없으면 ORDER_NOT_FOUND 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public Orders getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 주문을 취소 상태로 전이하고 취소 이력(CANCEL)을 저장한다. 주문 상태 변경은 영속 상태 dirty checking 으로 반영된다.
     */
    @Transactional
    public void cancel(Orders order, CancelerCd cancelerCd) {
        order.cancel();
        cancelRepository.save(Cancel.create(order.getOrderId(), cancelerCd, false));
    }
}
