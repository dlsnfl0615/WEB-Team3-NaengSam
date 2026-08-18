package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderCursor;
import com.naengsam.quick.domain.order.dto.OrderStatusCountDto;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

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
     * 로그인한 사용자가 role(부르미/드리미)로 참여한 주문을 최신순으로 커서 페이지네이션 조회한다. {@code statusFilter}가
     * 비어 있으면(전체 탭) {@link OrderCd#values()} 전체로 채워 넘긴다 — 화면의 필터 탭 하나가 여러 상태를 묶는 경우
     * (예: "진행중")까지 이미 프론트에서 구체적인 상태 목록으로 넘겨준다고 가정한다. 한 페이지보다 하나 더 가져와 보고
     * {@code hasNext}를 판단한 뒤, 실제로는 요청한 크기만큼만 잘라 돌려준다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getOrders(UUID userId, Role role, List<OrderCd> statusFilter, String cursorToken,
            Integer size) {
        OrderCursor cursor = OrderCursor.decode(cursorToken)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_CURSOR));
        Pageable pageable = PageRequest.of(0, resolvePageSize(size) + 1);
        List<OrderCd> orderCds = resolveOrderCds(statusFilter);

        List<OrderSummaryDto> rows;
        if (role == Role.BOORMI) {
            rows = orderRepository.findPageByBoormiId(userId, orderCds, cursor.deliveryRequestDtm(),
                    cursor.orderId(), pageable);
        } else {
            rows = orderRepository.findPageByDreamiId(userId, orderCds, cursor.deliveryRequestDtm(),
                    cursor.orderId(), pageable);
        }

        return toPageResponse(rows, resolvePageSize(size));
    }

    /**
     * 활동 내역 화면의 상태별(전체/진행중/완료/취소) 탭 개수. 화면 진입 시 한 번만 호출해 탭 전환마다 다시 세지 않는다.
     */
    @Transactional(readOnly = true)
    public List<OrderStatusCountDto> getStatusCounts(UUID userId, Role role) {
        if (role == Role.BOORMI) {
            return orderRepository.countGroupedByOrderCdForBoormi(userId);
        }
        return orderRepository.countGroupedByOrderCdForDreami(userId);
    }

    /**
     * 필터 탭이 비어 있으면(전체 탭) {@link OrderCd#values()} 전체로 채운다.
     */
    private List<OrderCd> resolveOrderCds(List<OrderCd> statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return List.of(OrderCd.values());
        }
        return statusFilter;
    }

    private BoormiOrdersResponse toPageResponse(List<OrderSummaryDto> rows, int pageSize) {
        boolean hasNext = rows.size() > pageSize;
        if (!hasNext) {
            return BoormiOrdersResponse.of(rows, null, false);
        }
        List<OrderSummaryDto> page = rows.subList(0, pageSize);
        String nextCursor = OrderCursor.of(page.getLast()).encode();
        return BoormiOrdersResponse.of(page, nextCursor, true);
    }

    private int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
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
     * 주문을 조회한다. 없으면 빈 Optional을 반환한다(호출자가 "주문 없음"을 예외가 아닌 정상 분기로 다뤄야 할 때 사용).
     */
    @Transactional(readOnly = true)
    public Optional<Orders> findOrder(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * 여러 주문을 한 번에 조회한다(orderId 하나당 쿼리를 날리는 N+1을 피하기 위함). 존재하지 않는 orderId는 결과
     * 맵에서 그냥 빠진다.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Orders> findOrders(Collection<UUID> orderIds) {
        return orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Orders::getOrderId, order -> order));
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
