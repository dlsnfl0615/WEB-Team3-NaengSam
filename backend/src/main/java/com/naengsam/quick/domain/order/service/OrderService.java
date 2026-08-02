package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
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
}
