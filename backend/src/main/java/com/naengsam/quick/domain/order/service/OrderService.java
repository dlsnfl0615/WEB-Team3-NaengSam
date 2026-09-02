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
        // 1) cursorToken(클라이언트가 이전 응답에서 그대로 받아온 opaque 문자열)을 OrderCursor(정렬 기준 값)로 되돌린다.
        //    OrderCursor.decode(...)는 Optional<OrderCursor>를 반환한다 — cursorToken이 null/공백(첫 페이지 요청)이면
        //    "커서 없음"을 뜻하는 빈 커서를 담아 정상적으로 값이 있는 Optional을 돌려주고, Base64 디코딩이나 파싱 자체가
        //    깨진(형식이 이상한) 값일 때만 Optional.empty()가 되어 아래 orElseThrow가 INVALID_CURSOR 예외를 던진다.
        OrderCursor cursor = OrderCursor.decode(cursorToken)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_CURSOR));
        // 2) Pageable: Spring Data JPA가 페이지 조회(LIMIT/OFFSET 등)에 쓰는 표준 타입. PageRequest.of(0, n)은
        //    "0번째 페이지, 한 번에 n건"을 의미한다. 여기서는 오프셋 페이지네이션이 아니라 커서 방식이라 페이지 번호(0)는
        //    항상 고정이고, size 대신 "요청 크기 + 1"을 넘긴다 — hasNext 판단을 위해 한 건 더 가져와 보는 트릭(아래 4번 참고).
        Pageable pageable = PageRequest.of(0, resolvePageSize(size) + 1);
        // 3) statusFilter가 비어 있으면(전체 탭) OrderCd.values() 전체로 채워, 쿼리 입장에서는 "필터 있음/없음" 분기가
        //    필요 없게(항상 IN 절 하나로) 통일한다.
        List<OrderCd> orderCds = resolveOrderCds(statusFilter);

        // 4) role(BOORMI/DREAMI)에 따라 완전히 다른 두 쿼리 메서드를 호출한다. 부르미/드리미를 하나의 OR 조건
        //    쿼리로 합치지 않고 이렇게 나눈 이유는 overview.md 8절에 정리돼 있다 — 파라미터 의존 OR 조건은 MySQL이
        //    인덱스를 확정적으로 타지 못할 수 있어서, role별로 각자의 인덱스(dreami_id 기준/boormi_id 기준)를
        //    확실히 타도록 쿼리 자체를 나눴다.
        //    cursor.deliveryRequestDtm()/cursor.orderId(): "이 시각·이 id보다 이전 것들만" 조회하는 조건으로 쓰인다
        //    (첫 페이지면 둘 다 null이라 조건 없이 최신부터 조회).
        List<OrderSummaryDto> rows;
        if (role == Role.BOORMI) {
            rows = orderRepository.findPageByBoormiId(userId, orderCds, cursor.deliveryRequestDtm(),
                    cursor.orderId(), pageable);
        } else {
            rows = orderRepository.findPageByDreamiId(userId, orderCds, cursor.deliveryRequestDtm(),
                    cursor.orderId(), pageable);
        }

        // 5) "요청 크기+1"건 중 실제로 몇 건을 보여줄지, 다음 페이지 커서는 뭔지는 toPageResponse가 판단한다.
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
    // OrderCd.values(): enum의 모든 값을 배열로 돌려주는 자바 표준 메서드. List.of(배열...)로 감싸 불변 리스트로 만든다.
    private List<OrderCd> resolveOrderCds(List<OrderCd> statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return List.of(OrderCd.values());
        }
        return statusFilter;
    }

    // getOrders가 "요청 크기+1"건을 가져온 뒤, 실제로 화면에 보여줄 목록과 다음 페이지 커서를 결정하는 곳.
    private BoormiOrdersResponse toPageResponse(List<OrderSummaryDto> rows, int pageSize) {
        // 쿼리에서 (pageSize+1)건을 요청했는데 실제로 pageSize보다 많이 왔다는 건 "여분 1건"이 있다는 뜻 =
        // 다음 페이지가 더 있다는 신호. 이 여분 덕분에 별도 COUNT 쿼리 없이 hasNext를 판단할 수 있다.
        boolean hasNext = rows.size() > pageSize;
        if (!hasNext) {
            // 더 볼 게 없으니 받은 그대로 반환하고 nextCursor는 null(더 이상 페이지 요청할 필요 없다는 뜻).
            return BoormiOrdersResponse.of(rows, null, false);
        }
        // subList(0, pageSize): 여분으로 가져온 마지막 1건은 잘라내고, 원래 요청한 개수만큼만 응답에 담는다.
        List<OrderSummaryDto> page = rows.subList(0, pageSize);
        // getLast(): 리스트의 마지막 원소를 꺼내는 자바 표준 메서드(Java 21+). 그 마지막 행의 정렬 키로
        // 새 OrderCursor를 만들고 Base64 문자열로 인코딩해, 클라이언트가 다음 요청에 그대로 실어 보내게 한다.
        String nextCursor = OrderCursor.of(page.getLast()).encode();
        return BoormiOrdersResponse.of(page, nextCursor, true);
    }

    // 요청한 size가 없거나 0 이하이면 기본값(20), 있으면 최대값(50)을 넘지 않게 잘라준다(과도한 조회 방지).
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
