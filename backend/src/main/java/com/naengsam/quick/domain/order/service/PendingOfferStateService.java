package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭엔진의 부르미 확정 액션이 큐에 쌓여 있는 동안 DB 주문이 그 사이 다른 경로로 바뀌었는지 확인한다.
 * {@code MatchingService}가 {@link OrderRepository}를 직접 다루지 않도록 이 서비스로 분리했다.
 */
@Service
@RequiredArgsConstructor
public class PendingOfferStateService {

    private final OrderRepository orderRepository;

    /**
     * 주어진 orderId/dreamiId가 DB 주문의 현재 확정 상태와 정확히 일치하는지 확인한다. offerId 자체의 신선도는
     * {@code BoormiService.confirmDreami}의 잠금 트랜잭션에서 이미 검증됐고, 그 트랜잭션이 커밋되며
     * {@code pendingOfferId}를 비웠으므로 여기서는 더 이상 확인할 수 없다(항상 null).
     *
     * @return 주문이 존재하고, {@code order_cd}가 {@link OrderCd#IN_PROGRESS}이며,
     *         {@code dreami_id}가 dreamiId와 같으면 true
     */
    @Transactional(readOnly = true)
    public boolean isCurrent(UUID orderId, UUID dreamiId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getOrderCd() == OrderCd.IN_PROGRESS)
                .filter(order -> dreamiId.equals(order.getDreamiId()))
                .isPresent();
    }
}
