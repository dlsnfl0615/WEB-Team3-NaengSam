package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 부르미 응답 timeout을 DB 주문에 반영하는 {@link BoormiOfferExpirationService}. 이미 부르미가 확정/거절해 DB가 더 이상
 * 이 드리미의 PENDING_BOORMI_CONFIRMATION이 아니게 됐으면(경합 패배) 아무것도 바꾸지 않고 false를 반환해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class BoormiOfferExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private BoormiOfferExpirationService boormiOfferExpirationService;

    private static Orders order(UUID dreamiId, OrderCd orderCd) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), UUID.randomUUID(), point, point);
        ReflectionTestUtils.setField(order, "orderCd", orderCd);
        ReflectionTestUtils.setField(order, "dreamiId", dreamiId);
        return order;
    }

    @Test
    void PENDING_BOORMI_CONFIRMATION이고_같은_드리미면_MATCHING으로_되돌리고_true를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(dreamiId, OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

        boolean expired = boormiOfferExpirationService.expire(orderId, dreamiId);

        assertThat(expired).isTrue();
        assertThat(order.getOrderCd()).isEqualTo(OrderCd.MATCHING);
        assertThat(order.getDreamiId()).isNull();
    }

    @Test
    void 이미_부르미가_확정해_IN_PROGRESS면_아무것도_바꾸지_않고_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(dreamiId, OrderCd.IN_PROGRESS);
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

        boolean expired = boormiOfferExpirationService.expire(orderId, dreamiId);

        assertThat(expired).isFalse();
        assertThat(order.getOrderCd()).isEqualTo(OrderCd.IN_PROGRESS);
        assertThat(order.getDreamiId()).isEqualTo(dreamiId);
    }

    @Test
    void 이미_부르미가_거절해_MATCHING이면_아무것도_바꾸지_않고_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(null, OrderCd.MATCHING);
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

        boolean expired = boormiOfferExpirationService.expire(orderId, dreamiId);

        assertThat(expired).isFalse();
        assertThat(order.getOrderCd()).isEqualTo(OrderCd.MATCHING);
    }

    @Test
    void PENDING_BOORMI_CONFIRMATION이어도_dreamiId가_다르면_false를_반환한다() {
        // 그 사이 다른 드리미가 새로 수락해 이미 다른 사람의 확인 대기로 넘어간 상황.
        UUID orderId = UUID.randomUUID();
        UUID expiredDreamiId = UUID.randomUUID();
        UUID currentDreamiId = UUID.randomUUID();
        Orders order = order(currentDreamiId, OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

        boolean expired = boormiOfferExpirationService.expire(orderId, expiredDreamiId);

        assertThat(expired).isFalse();
        assertThat(order.getOrderCd()).isEqualTo(OrderCd.PENDING_BOORMI_CONFIRMATION);
        assertThat(order.getDreamiId()).isEqualTo(currentDreamiId);
    }

    @Test
    void 주문이_없으면_ORDER_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> boormiOfferExpirationService.expire(orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }
}
