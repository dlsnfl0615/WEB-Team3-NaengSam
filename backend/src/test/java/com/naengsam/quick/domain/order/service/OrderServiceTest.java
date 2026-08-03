package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.order.entity.Cancel;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.CancelRepository;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 주문 조회/취소(상태 전이 + 취소 이력 저장) 로직 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CancelRepository cancelRepository;

    @InjectMocks
    private OrderService orderService;

    private static Orders matchingOrder(UUID orderId) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        return Orders.create(orderId, UUID.randomUUID(), point, point);
    }

    @Test
    void 주문조회_없으면_ORDER_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> orderService.getOrder(orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 취소_상태를_CANCELLED로_바꾸고_취소이력을_저장한다() {
        UUID orderId = UUID.randomUUID();
        Orders order = matchingOrder(orderId);

        orderService.cancel(order, CancelerCd.BOORMI);

        assertThat(order.getOrderCd()).isEqualTo(OrderCd.CANCELLED);

        ArgumentCaptor<Cancel> captor = ArgumentCaptor.forClass(Cancel.class);
        then(cancelRepository).should().save(captor.capture());
        Cancel savedCancel = captor.getValue();
        assertThat(savedCancel.getOrderId()).isEqualTo(orderId);
        assertThat(savedCancel.getCancelerCd()).isEqualTo(CancelerCd.BOORMI);
        assertThat(savedCancel.isPenaltyApplied()).isFalse();
    }
}
