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
     * 주어진 orderId/offerId/dreamiId가 DB 주문의 현재 확정 대기 상태와 정확히 일치하는지 확인한다.
     *
     * @return 주문이 존재하고, {@code order_cd}가 {@link OrderCd#PENDING_BOORMI_CONFIRMATION}이며,
     *         {@code pending_offer_id}와 {@code dreami_id}가 각각 offerId/dreamiId와 같으면 true
     */
    @Transactional(readOnly = true)
    public boolean isCurrent(UUID orderId, UUID offerId, UUID dreamiId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getOrderCd() == OrderCd.PENDING_BOORMI_CONFIRMATION)
                .filter(order -> offerId.equals(order.getPendingOfferId()))
                .filter(order -> dreamiId.equals(order.getDreamiId()))
                .isPresent();
    }
}
