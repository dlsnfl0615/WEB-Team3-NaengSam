package com.naengsam.quick.domain.boormi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.exception.PaymentErrorCode;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 주문 접수의 DB 쓰기 구간(저장·결제·매칭 시작 이벤트) 단위 테스트. 카카오 호출은 이 트랜잭션 밖에서 이미 끝나 있어야 한다(#437).
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderPlacementService orderPlacementService;

    private static Orders order(UUID boormiId) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        return Orders.create(UUID.randomUUID(), boormiId, point, point);
    }

    @Test
    void 주문을_저장한_뒤_같은_orderId로_결제한다() {
        UUID boormiId = UUID.randomUUID();
        Orders orders = order(boormiId);

        UUID result = orderPlacementService.place(orders, 10100L);

        assertThat(result).isEqualTo(orders.getOrderId());

        InOrder inOrder = inOrder(orderService, paymentService);
        inOrder.verify(orderService).createOrders(orders);
        inOrder.verify(paymentService).payWithPoint(boormiId, orders.getOrderId(), 10100L);
    }

    @Test
    void 정상이면_커밋후_처리용_매칭시작_이벤트를_발행한다() {
        Orders orders = order(UUID.randomUUID());

        orderPlacementService.place(orders, 10100L);

        then(eventPublisher).should().publishEvent(new MatchingStartRequestedEvent(orders));
        then(matchingService).should(never()).startMatching(any()); // 커밋 전에는 엔진에 직접 제출하지 않는다
    }

    @Test
    void 결제가_실패하면_매칭시작_이벤트를_발행하지_않는다() {
        Orders orders = order(UUID.randomUUID());
        willThrow(new BusinessException(PaymentErrorCode.INSUFFICIENT_POINT))
                .given(paymentService).payWithPoint(any(), any(), anyLong());

        Throwable thrown = catchThrowable(() -> orderPlacementService.place(orders, 10100L));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(PaymentErrorCode.INSUFFICIENT_POINT);
        then(eventPublisher).should(never()).publishEvent(any(MatchingStartRequestedEvent.class));
    }

    @Test
    void 이미_진행중인_매칭방이_있으면_CONFLICT이고_이벤트를_발행하지_않는다() {
        Orders orders = order(UUID.randomUUID());
        given(matchingService.isActiveGroupExists(orders.getOrderId())).willReturn(true);

        Throwable thrown = catchThrowable(() -> orderPlacementService.place(orders, 10100L));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.CONFLICT);
        then(eventPublisher).should(never()).publishEvent(any(MatchingStartRequestedEvent.class));
    }
}
