package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.user.dto.ActiveContext;
import com.naengsam.quick.domain.user.dto.ActiveRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사용자의 현재 수행 상태 판정 단위 테스트. 주문(DB)과 매칭 대기(인메모리)를 합쳐 역할을 결정하는 우선순위를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UserActivityResolverTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private UserActivityResolver userActivityResolver;

    /**
     * 스텁 조립 중첩을 피하려고 {@code given(...)} 인자 안에서 호출하지 않고 미리 만들어 둔다.
     */
    private static Orders order(UUID orderId, OrderCd orderCd) {
        Orders order = Mockito.mock(Orders.class);
        Mockito.doReturn(orderId).when(order).getOrderId();
        Mockito.doReturn(orderCd).when(order).getOrderCd();
        return order;
    }

    private void noDreamiOrder() {
        given(orderRepository.findByDreamiIdAndOrderCdIn(any(), anyCollection())).willReturn(Optional.empty());
    }

    private void noBoormiOrder() {
        given(orderRepository.findFirstByBoormiIdAndOrderCdInOrderByDeliveryRequestDtmDesc(any(), anyCollection()))
                .willReturn(Optional.empty());
    }

    @Test
    void 드리미로_배달중이면_DREAMI와_주문을_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders dreamiOrder = order(orderId, OrderCd.IN_PROGRESS);
        given(orderRepository.findByDreamiIdAndOrderCdIn(any(), anyCollection()))
                .willReturn(Optional.of(dreamiOrder));

        ActiveContext result = userActivityResolver.resolve(userId);

        assertThat(result.role()).isEqualTo(ActiveRole.DREAMI);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.orderCd()).isEqualTo(OrderCd.IN_PROGRESS);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void 드리미가_수락하고_부르미_확인을_기다리면_BOORMI가_아니라_DREAMI로_판정한다() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders dreamiOrder = order(orderId, OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderRepository.findByDreamiIdAndOrderCdIn(any(), anyCollection()))
                .willReturn(Optional.of(dreamiOrder));

        ActiveContext result = userActivityResolver.resolve(userId);

        assertThat(result.role()).isEqualTo(ActiveRole.DREAMI);
        assertThat(result.orderCd()).isEqualTo(OrderCd.PENDING_BOORMI_CONFIRMATION);
        // 드리미로 먼저 걸리므로 부르미 조회와 인메모리 조회까지 갈 필요가 없다.
        verify(orderRepository, never())
                .findFirstByBoormiIdAndOrderCdInOrderByDeliveryRequestDtmDesc(any(), anyCollection());
        verify(matchingService, never()).isDreamiWaiting(any());
    }

    @Test
    void 부르미가_주문을_등록해_매칭중이면_BOORMI와_주문을_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders boormiOrder = order(orderId, OrderCd.MATCHING);
        noDreamiOrder();
        given(orderRepository.findFirstByBoormiIdAndOrderCdInOrderByDeliveryRequestDtmDesc(any(), anyCollection()))
                .willReturn(Optional.of(boormiOrder));

        ActiveContext result = userActivityResolver.resolve(userId);

        assertThat(result.role()).isEqualTo(ActiveRole.BOORMI);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.orderCd()).isEqualTo(OrderCd.MATCHING);
        verify(matchingService, never()).isDreamiWaiting(any());
    }

    @Test
    void 주문은_없지만_드리미가_매칭_대기중이면_주문없는_DREAMI를_반환한다() {
        UUID userId = UUID.randomUUID();
        noDreamiOrder();
        noBoormiOrder();
        given(matchingService.isDreamiWaiting(userId)).willReturn(true);

        ActiveContext result = userActivityResolver.resolve(userId);

        assertThat(result.role()).isEqualTo(ActiveRole.DREAMI);
        assertThat(result.orderId()).isNull();
        assertThat(result.orderCd()).isNull();
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void 주문도_없고_매칭_대기중도_아니면_비활성을_반환한다() {
        UUID userId = UUID.randomUUID();
        noDreamiOrder();
        noBoormiOrder();
        given(matchingService.isDreamiWaiting(userId)).willReturn(false);

        ActiveContext result = userActivityResolver.resolve(userId);

        assertThat(result.role()).isNull();
        assertThat(result.orderId()).isNull();
        assertThat(result.isActive()).isFalse();
    }
}
