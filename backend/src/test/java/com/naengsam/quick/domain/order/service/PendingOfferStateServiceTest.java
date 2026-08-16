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
 * 매칭엔진의 부르미 확정 액션이 실행되는 시점에, DB 주문이 여전히 그 오퍼/드리미와 일치하는 확정 대기 상태인지
 * {@link PendingOfferStateService}가 정확히 판별하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PendingOfferStateServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PendingOfferStateService pendingOfferStateService;

    private static Orders order(OrderCd orderCd, UUID dreamiId, UUID pendingOfferId) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), UUID.randomUUID(), point, point);
        ReflectionTestUtils.setField(order, "orderCd", orderCd);
        ReflectionTestUtils.setField(order, "dreamiId", dreamiId);
        ReflectionTestUtils.setField(order, "pendingOfferId", pendingOfferId);
        return order;
    }

    @Test
    void 주문_상태가_PENDING_BOORMI_CONFIRMATION이고_offerId_dreamiId가_모두_일치하면_true를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.PENDING_BOORMI_CONFIRMATION, dreamiId, offerId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, offerId, dreamiId);

        assertThat(current).isTrue();
    }

    @Test
    void 주문_상태가_PENDING_BOORMI_CONFIRMATION이_아니면_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.MATCHING, dreamiId, offerId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, offerId, dreamiId);

        assertThat(current).isFalse();
    }

    @Test
    void pending_offer_id가_다르면_false를_반환한다() {
        // 그 사이 새 오퍼 라운드가 돌아 pendingOfferId가 갱신된 상황 - 확정 요청이 들고 온 offerId는 옛 라운드의 것.
        UUID orderId = UUID.randomUUID();
        UUID staleOfferId = UUID.randomUUID();
        UUID currentOfferId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.PENDING_BOORMI_CONFIRMATION, dreamiId, currentOfferId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, staleOfferId, dreamiId);

        assertThat(current).isFalse();
    }

    @Test
    void dreami_id가_다르면_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID currentDreamiId = UUID.randomUUID();
        UUID otherDreamiId = UUID.randomUUID();
        Orders order = order(OrderCd.PENDING_BOORMI_CONFIRMATION, currentDreamiId, offerId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        boolean current = pendingOfferStateService.isCurrent(orderId, offerId, otherDreamiId);

        assertThat(current).isFalse();
    }

    @Test
    void 주문이_존재하지_않으면_false를_반환한다() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        boolean current = pendingOfferStateService.isCurrent(orderId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(current).isFalse();
    }
}
