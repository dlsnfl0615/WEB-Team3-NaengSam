package com.naengsam.quick.domain.boormi.service;

import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 접수의 DB 쓰기 구간만 담는 트랜잭션 경계. 주문 저장·포인트 결제를 한 트랜잭션으로 묶고, 커밋 후 매칭이 시작되도록 이벤트를 발행한다.
 * <p>
 * {@link BoormiService#subscribeOrder}가 카카오 API 를 호출하는 동안 DB 커넥션을 붙들지 않도록 쓰기 구간만 별도 빈으로 분리했다(#437). 같은 이유로 외부 API 호출을 이
 * 클래스 안에 넣으면 안 된다.
 * <p>
 * {@link BoormiService} 안의 메서드로 두지 않은 이유: 자기 호출은 프록시를 거치지 않아 트랜잭션이 아예 걸리지 않는다. {@link OrderService} 에 합치지 않은 이유:
 * MatchingService → DeliveryService → OrderService 참조가 이미 있어 순환 의존이 된다.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final MatchingService matchingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문을 저장하고 포인트로 결제한다. 결제가 실패하면 주문 저장까지 함께 롤백된다.
     */
    @Transactional
    public UUID place(Orders orders, long amount) {
        orderService.createOrders(orders);
        paymentService.payWithPoint(orders.getBoormiId(), orders.getOrderId(), amount);
        if (matchingService.isActiveGroupExists(orders.getOrderId())) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
        // 엔진은 매칭 시작 즉시 드리미에게 오퍼 팝업을 보내므로, 주문이 커밋된 뒤에 제출해야 드리미가 그 주문을 조회할 수 있다.
        eventPublisher.publishEvent(new MatchingStartRequestedEvent(orders));
        return orders.getOrderId();
    }
}
