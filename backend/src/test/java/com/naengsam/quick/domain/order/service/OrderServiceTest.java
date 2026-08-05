package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderCursor;
import com.naengsam.quick.domain.order.entity.Cancel;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.CancelRepository;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    /**
     * 커서 테스트용 주문. delivery_request_dtm 은 insertable=false 라 setter 가 없으므로 리플렉션으로 강제한다.
     */
    private static Orders orderAt(UUID boormiId, LocalDateTime dtm) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), boormiId, point, point);
        ReflectionTestUtils.setField(order, "deliveryRequestDtm", dtm);
        return order;
    }

    @Test
    void 첫_페이지_조회시_size초과분이_있으면_hasNext가_true이고_nextCursor를_반환한다() {
        UUID boormiId = UUID.randomUUID();
        Orders first = orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 12, 0));
        Orders second = orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 11, 0));
        Orders overflow = orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 10, 0));
        // size=2 이므로 size+1=3 개 조회 → 3개 반환(초과분 존재)
        given(orderRepository.findFirstPageByBoormi(any(), any(), anyInt()))
                .willReturn(List.of(first, second, overflow));

        BoormiOrdersResponse response = orderService.getBoormiOrders(boormiId, null, 2, null);

        assertThat(response.orders()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor())
                .isEqualTo(new OrderCursor(second.getDeliveryRequestDtm(), second.getOrderId()).encode());
        then(orderRepository).should().findFirstPageByBoormi(boormiId, null, 3);
    }

    @Test
    void 마지막_페이지면_hasNext가_false이고_nextCursor는_null이다() {
        UUID boormiId = UUID.randomUUID();
        Orders only = orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 12, 0));
        given(orderRepository.findFirstPageByBoormi(any(), any(), anyInt()))
                .willReturn(List.of(only));

        BoormiOrdersResponse response = orderService.getBoormiOrders(boormiId, null, 2, null);

        assertThat(response.orders()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void status가_주어지면_해당_상태이름을_필터로_레포지토리에_전달한다() {
        UUID boormiId = UUID.randomUUID();
        given(orderRepository.findFirstPageByBoormi(any(), any(), anyInt()))
                .willReturn(List.of());

        orderService.getBoormiOrders(boormiId, null, 20, OrderCd.MATCHING);

        then(orderRepository).should().findFirstPageByBoormi(eq(boormiId), eq("MATCHING"), anyInt());
    }

    @Test
    void status가_null이면_null필터를_레포지토리에_전달한다() {
        UUID boormiId = UUID.randomUUID();
        given(orderRepository.findFirstPageByBoormi(any(), any(), anyInt()))
                .willReturn(List.of());

        orderService.getBoormiOrders(boormiId, null, 20, null);

        then(orderRepository).should().findFirstPageByBoormi(eq(boormiId), isNull(), anyInt());
    }

    @Test
    void 커서가_주어지면_디코딩된_dtm과_id로_afterCursor_쿼리를_호출한다() {
        UUID boormiId = UUID.randomUUID();
        LocalDateTime cursorDtm = LocalDateTime.of(2026, 8, 3, 12, 0);
        UUID cursorId = UUID.randomUUID();
        String cursor = new OrderCursor(cursorDtm, cursorId).encode();
        given(orderRepository.findPageByBoormiAfterCursor(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());

        orderService.getBoormiOrders(boormiId, cursor, 20, null);

        then(orderRepository).should()
                .findPageByBoormiAfterCursor(eq(boormiId), isNull(), eq(cursorDtm), eq(cursorId), anyInt());
    }

    @Test
    void 잘못된_커서면_INVALID_CURSOR_예외() {
        UUID boormiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(
                () -> orderService.getBoormiOrders(boormiId, "!!!not-base64!!!", 20, null));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_CURSOR);
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

    @Test
    void orderId로_취소하면_주문을_조회해_CANCELLED로_바꾸고_취소이력을_저장한다() {
        UUID orderId = UUID.randomUUID();
        Orders order = matchingOrder(orderId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        orderService.cancel(orderId, CancelerCd.DREAMI);

        assertThat(order.getOrderCd()).isEqualTo(OrderCd.CANCELLED);

        ArgumentCaptor<Cancel> captor = ArgumentCaptor.forClass(Cancel.class);
        then(cancelRepository).should().save(captor.capture());
        assertThat(captor.getValue().getCancelerCd()).isEqualTo(CancelerCd.DREAMI);
    }

    @Test
    void 완료하면_주문을_조회해_COMPLETED로_바꾼다() {
        UUID orderId = UUID.randomUUID();
        Orders order = matchingOrder(orderId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        orderService.complete(orderId);

        assertThat(order.getOrderCd()).isEqualTo(OrderCd.COMPLETED);
    }

    @Test
    void 완료할_주문이_없으면_ORDER_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> orderService.complete(orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }
}
