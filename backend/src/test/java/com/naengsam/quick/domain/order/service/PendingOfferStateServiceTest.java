package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
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
 * 매칭엔진의 부르미 확정 액션이 실행되는 시점에, DB 주문이 여전히 그 확정과 일치하는 상태인지
 * {@link PendingOfferStateService}가 정확히 판별하는지 검증한다. BoormiService.confirmDreami 커밋 시점에
 * 주문은 이미 IN_PROGRESS로 전이되고 pendingOfferId는 비워지므로, 이 시점의 진실은 IN_PROGRESS + dreamiId 일치다.
 */
@ExtendWith(MockitoExtension.class)
class PendingOfferStateServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PendingOfferStateService pendingOfferStateService;

    private static Orders order(OrderCd orderCd, UUID dreamiId) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), UUID.randomUUID(), point, point);
        ReflectionTestUtils.setField(order, "orderCd", orderCd);
        ReflectionTestUtils.setField(order, "dreamiId", dreamiId);
        return order;
    }

    @Test
    void 주문_상태가_IN_PROGRESS이고_dreamiId가_일치하면_true를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.IN_PROGRESS, dreamiId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, dreamiId);

        assertThat(current).isTrue();
    }

    @Test
    void 주문_상태가_IN_PROGRESS가_아니면_false를_반환한다() {
        // 타임아웃이 먼저 처리돼 이미 MATCHING으로 되돌아간 뒤 뒤늦게 실행되는 경우.
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.MATCHING, dreamiId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, dreamiId);

        assertThat(current).isFalse();
    }

    @Test
    void dreami_id가_다르면_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID currentDreamiId = UUID.randomUUID();
        UUID otherDreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.IN_PROGRESS, currentDreamiId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, otherDreamiId);

        assertThat(current).isFalse();
    }

    @Test
    void 주문이_존재하지_않으면_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        boolean current = pendingOfferStateService.isCurrent(orderId, UUID.randomUUID());

        assertThat(current).isFalse();
    }
}
