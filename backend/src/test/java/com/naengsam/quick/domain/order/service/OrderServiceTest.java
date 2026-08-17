package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 주문 조회/취소(상태 전이 + 취소 이력 저장) 로직 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final List<OrderCd> ALL_ORDER_CDS = List.of(OrderCd.values());

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
     * 특정 시각으로 등록된 주문. delivery_request_dtm 은 insertable=false 라 setter 가 없으므로 리플렉션으로 강제한다.
     */
    private static Orders orderAt(UUID boormiId, LocalDateTime dtm) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), boormiId, point, point);
        ReflectionTestUtils.setField(order, "deliveryRequestDtm", dtm);
        return order;
    }

    @Test
    void 주문목록조회_role이_BOORMI이면_boormi_id_기준으로_커서_첫페이지를_조회한다() {
        UUID boormiId = UUID.randomUUID();
        OrderSummaryDto first = OrderSummaryDto.from(orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 12, 0)));
        OrderSummaryDto second = OrderSummaryDto.from(orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 11, 0)));
        given(orderRepository.findPageByBoormiId(eq(boormiId), eq(ALL_ORDER_CDS), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(first, second));

        BoormiOrdersResponse response = orderService.getOrders(boormiId, Role.BOORMI, null, null, null);

        assertThat(response.orders()).containsExactly(first, second);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        then(orderRepository).should()
                .findPageByBoormiId(eq(boormiId), eq(ALL_ORDER_CDS), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void 주문목록조회_role이_DREAMI이면_dreami_id_기준으로_커서_첫페이지를_조회한다() {
        UUID dreamiId = UUID.randomUUID();
        given(orderRepository.findPageByDreamiId(eq(dreamiId), eq(ALL_ORDER_CDS), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        orderService.getOrders(dreamiId, Role.DREAMI, null, null, null);

        then(orderRepository).should()
                .findPageByDreamiId(eq(dreamiId), eq(ALL_ORDER_CDS), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void 주문목록조회_상태필터를_지정하면_그대로_넘긴다() {
        UUID boormiId = UUID.randomUUID();
        List<OrderCd> ongoing = List.of(OrderCd.MATCHING, OrderCd.PENDING_BOORMI_CONFIRMATION,
                OrderCd.IN_PROGRESS, OrderCd.WAITING_CONFIRMATION);
        given(orderRepository.findPageByBoormiId(eq(boormiId), eq(ongoing), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        orderService.getOrders(boormiId, Role.BOORMI, ongoing, null, null);

        then(orderRepository).should()
                .findPageByBoormiId(eq(boormiId), eq(ongoing), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void 주문목록조회_결과가_페이지크기보다_많으면_초과분을_잘라내고_hasNext를_true로_반환한다() {
        UUID boormiId = UUID.randomUUID();
        List<OrderSummaryDto> pagePlusOne = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> OrderSummaryDto.from(orderAt(boormiId, LocalDateTime.of(2026, 8, 3, 12, 0).minusMinutes(i))))
                .toList();
        given(orderRepository.findPageByBoormiId(eq(boormiId), eq(ALL_ORDER_CDS), isNull(), isNull(), any(Pageable.class)))
                .willReturn(pagePlusOne);

        BoormiOrdersResponse response = orderService.getOrders(boormiId, Role.BOORMI, null, null, null);

        assertThat(response.orders()).hasSize(20);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
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
    void 취소_이미_종료된_주문이면_CANNOT_CANCEL_예외이고_이력을_저장하지_않는다() {
        for (OrderCd terminal : List.of(OrderCd.CANCELLED, OrderCd.COMPLETED, OrderCd.CLAIM_REVIEW)) {
            Orders order = matchingOrder(UUID.randomUUID());
            ReflectionTestUtils.setField(order, "orderCd", terminal);

            Throwable thrown = catchThrowable(() -> orderService.cancel(order, CancelerCd.BOORMI));

            assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.CANNOT_CANCEL);
            assertThat(order.getOrderCd()).isEqualTo(terminal);
        }
        then(cancelRepository).should(never()).save(any());
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
