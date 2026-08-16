package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.Cancel;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.entity.Role;
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
     * 로그인한 사용자가 role(부르미/드리미)로 참여한 주문 전체를 최신순으로 조회한다. 필터링(전체/진행중/완료/취소)은
     * 클라이언트에서 하므로 여기서는 상태 무관하게 전체를 반환한다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getOrders(UUID userId, Role role) {
        List<Orders> rows = orderRepository.findAllByRole(userId, role.name());
        List<OrderSummaryDto> orders = rows.stream().map(OrderSummaryDto::from).toList();
        return BoormiOrdersResponse.of(orders);
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
     * 주문을 비관적 쓰기 락으로 조회한다. 상태를 확인하고 곧바로 바꾸는 경로(취소 등)에서 쓰며, 먼저 락을 잡은 트랜잭션이 끝날 때까지 나머지는 대기했다가
     * 최신 상태를 다시 읽으므로 read-check-write 레이스가 닫힌다. 없으면 ORDER_NOT_FOUND 예외를 던진다.
     * <p>
     * FOR UPDATE 는 읽기 전용 트랜잭션에서 쓸 수 없으므로 조회만 하는 곳에서는 {@link #getOrder(UUID)} 를 쓴다.
     */
    @Transactional
    public Orders getOrderForUpdate(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 주문을 취소 상태로 전이하고 취소 이력(CANCEL)을 저장한다. 이미 종료된 주문(취소·완료·클레임)은 취소할 수 없어
     * CANNOT_CANCEL 예외를 던진다(호출자의 상태·소유권 검증과 무관하게 지켜야 하는 주문 자신의 불변식). 주문 상태 변경은
     * 영속 상태 dirty checking 으로 반영된다.
     */
    @Transactional
    public void cancel(Orders order, CancelerCd cancelerCd) {
        OrderCd status = order.getOrderCd();
        if (status == OrderCd.CANCELLED || status == OrderCd.COMPLETED || status == OrderCd.CLAIM_REVIEW) {
            throw new BusinessException(OrderErrorCode.CANNOT_CANCEL);
        }
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
