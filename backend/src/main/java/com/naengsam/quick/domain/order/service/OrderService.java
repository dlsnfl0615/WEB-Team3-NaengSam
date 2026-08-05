package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderCursor;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.Cancel;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.CancelRepository;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

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
     * 부르미가 신청한 주문을 최신순 커서 페이지네이션으로 조회한다. status 가 주어지면 해당 상태만 필터링한다. 다음 페이지 존재 여부는 size+1 개를 조회해 판단하고, 초과분은 잘라낸 뒤 마지막
     * 항목으로 다음 커서를 만든다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getBoormiOrders(UUID boormiId, String cursor, int size, OrderCd status) {
        int pageSize = Math.clamp(size, MIN_PAGE_SIZE, MAX_PAGE_SIZE);
        String statusFilter = status == null ? null : status.name();

        List<Orders> rows;
        if (cursor == null) {
            rows = orderRepository.findFirstPageByBoormi(boormiId, statusFilter, pageSize + 1);
        } else {
            OrderCursor decoded = OrderCursor.decode(cursor);
            rows = orderRepository.findPageByBoormiAfterCursor(
                    boormiId, statusFilter, decoded.dtm(), decoded.orderId(), pageSize + 1);
        }

        boolean hasNext = rows.size() > pageSize;
        List<Orders> page = hasNext ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasNext) {
            Orders last = page.getLast();
            nextCursor = new OrderCursor(last.getDeliveryRequestDtm(), last.getOrderId()).encode();
        }

        List<OrderSummaryDto> orders = page.stream().map(OrderSummaryDto::from).toList();
        return BoormiOrdersResponse.of(orders, nextCursor, hasNext);
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

    /**
     * 배달(픽업) 취소로 주문을 취소 상태로 전이하고 취소 이력을 저장한다. orderId 로 주문을 조회해 처리한다.
     */
    @Transactional
    public void cancel(UUID orderId, CancelerCd cancelerCd) {
        cancel(getOrder(orderId), cancelerCd);
    }

    /**
     * 배달이 완료되어 주문을 완료 상태로 전이한다. 주문 상태 변경은 영속 상태 dirty checking 으로 반영된다.
     */
    @Transactional
    public void complete(UUID orderId) {
        getOrder(orderId).complete();
    }
}
