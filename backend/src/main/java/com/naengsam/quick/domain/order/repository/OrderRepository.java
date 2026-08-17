package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.dto.CompletedSavingAggregate;
import com.naengsam.quick.domain.order.dto.NearbyCallOrderDto;
import com.naengsam.quick.domain.order.dto.OrderStatusCountDto;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Orders, UUID> {

    /**
     * 드리미가 지금 수행 중인 배달 건을 찾는다. 드리미는 한 번에 하나만 수행하므로 단건 조회다.
     */
    Optional<Orders> findByDreamiIdAndOrderCd(UUID dreamiId, OrderCd orderCd);

    /**
     * 드리미로서 진행 중인 건을 찾는다. 부르미 확인 대기(PENDING_BOORMI_CONFIRMATION)까지 포함해야
     * "수락은 했지만 아직 배달은 시작되지 않은" 구간이 빠지지 않는다. 드리미는 동시 1건이므로 단건이다.
     */
    Optional<Orders> findByDreamiIdAndOrderCdIn(UUID dreamiId, Collection<OrderCd> orderCds);

    /**
     * 부르미로서 진행 중인 건 중 가장 최근 것을 찾는다. 부르미는 동시에 여러 주문을 가질 수 있어(MAX_ACTIVE_ORDERS)
     * 최신 1건만 화면 복귀용으로 쓴다.
     */
    Optional<Orders> findFirstByBoormiIdAndOrderCdInOrderByDeliveryRequestDtmDesc(
            UUID boormiId, Collection<OrderCd> orderCds);

    // 드리미의 제안 수락 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 여러 드리미가 동시에 같은 주문을 수락해도, 먼저 락을 잡은 트랜잭션이 끝날 때까지 나머지는 대기했다가
    // 최신 상태(이미 MATCHING이 아님)를 다시 읽게 되므로 read-check-write 레이스가 닫힌다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Orders> findByOrderId(UUID orderId);

    /**
     * 드리미 대시보드의 완료 건수 집계용.
     */
    long countByDreamiIdAndOrderCd(UUID dreamiId, OrderCd orderCd);

    /**
     * 부르미 대시보드의 누적 완료 건수·결제액·시장 환산 재료를 물건 유형별로 집계한다. 시장 환산 금액이 물건 유형 배율을 타므로
     * 배율표를 SQL에 복제하는 대신 유형별로 나눠 받고 서비스에서 배율을 곱한다. {@code baseSection}(m)을 넘긴 거리만 초과 거리로 더한다.
     */
    @Query("SELECT new com.naengsam.quick.domain.order.dto.CompletedSavingAggregate("
            + "o.itemCd, COUNT(o), "
            + "COALESCE(SUM(CASE WHEN o.deliveryDistance > :baseSection "
            + "THEN o.deliveryDistance - :baseSection ELSE 0 END), 0), "
            + "COALESCE(SUM(o.deliveryAmount), 0)) "
            + "FROM Orders o WHERE o.boormiId = :boormiId "
            + "AND o.orderCd = com.naengsam.quick.domain.order.entity.OrderCd.COMPLETED "
            + "GROUP BY o.itemCd")
    List<CompletedSavingAggregate> aggregateCompletedSavingByBoormi(@Param("boormiId") UUID boormiId,
            @Param("baseSection") long baseSection);

    /**
     * 주변 콜 목록에 필요한 주문 컬럼만 id 목록으로 한 번에 조회한다. 대상은 최대 10건(MAX_NEARBY_ORDER_COUNT)이라 청크 분할이 필요 없다. {@code IN} 결과의 행
     * 순서는 보장되지 않으므로 거리순 정렬은 호출부가 다시 맞춘다.
     */
    @Query("SELECT new com.naengsam.quick.domain.order.dto.NearbyCallOrderDto("
            + "o.orderId, o.itemName, o.itemCd, o.orderCd, o.deliveryAmount, o.deliveryEta, "
            + "o.originAddressLine1, o.originAddressLine2, "
            + "o.destinationAddressLine1, o.destinationAddressLine2) "
            + "FROM Orders o WHERE o.orderId IN :orderIds")
    List<NearbyCallOrderDto> findNearbyCallOrders(@Param("orderIds") List<UUID> orderIds);

    /**
     * 드리미 활동 내역 화면의 전체 건수 집계용(상태 무관).
     */
    long countByDreamiId(UUID dreamiId);

    /**
     * 부르미 활동 내역 화면의 전체 건수 집계용(상태 무관).
     */
    long countByBoormiId(UUID boormiId);

    @Query(value = """
            SELECT COUNT(*) FROM ORDERS o
            WHERE o.order_cd NOT IN ('COMPLETED','CANCELLED','CLAIM_REVIEW')
              AND (o.boormi_id = :userId OR o.dreami_id = :userId)
            """, nativeQuery = true)
    long countActiveOrders(@Param("userId") UUID userId);

    /**
     * 활동 내역 목록의 커서 기반 페이지 조회(부르미). {@code orderCds}는 화면 필터 탭 하나가 여러 상태를 묶은 경우
     * (예: "진행중" = MATCHING/PENDING_BOORMI_CONFIRMATION/IN_PROGRESS/WAITING_CONFIRMATION)까지 커버해야 해서
     * 컬렉션으로 받는다 — "전체" 탭은 서비스 계층에서 {@code OrderCd.values()} 전체를 채워 넘긴다(항상 비어있지 않은
     * 컬렉션이 오므로 IN 바인딩에 null 걱정이 없다). {@code cursorDtm}이 null이면 첫 페이지(맨 최신부터), 아니면
     * 그보다 이전 것들만 — 최신순(delivery_request_dtm DESC, 동일 시각은 order_id DESC) 정렬을 그대로 이어간다.
     * {@code Orders} 엔티티 전체가 아니라 목록 화면에 필요한 컬럼만 {@code OrderSummaryDto}로 바로 투영해, route_path
     * 같은 대형 컬럼을 굳이 읽어 엔티티로 매핑하는 낭비를 없앤다.
     */
    @Query("""
            SELECT new com.naengsam.quick.domain.order.dto.OrderSummaryDto(
                o.orderId, o.itemName, o.itemCd, o.orderCd, o.deliveryAmount, o.deliveryEta,
                o.deliveryDistance, o.originLatitude, o.originLongitude, o.originAlias, o.originAddressLine1,
                o.destinationLatitude, o.destinationLongitude, o.destinationAlias, o.destinationAddressLine1,
                o.imageKey, o.deliveryRequest, o.deliveryRequestDtm)
            FROM Orders o
            WHERE o.boormiId = :userId
              AND o.orderCd IN :orderCds
              AND (:cursorDtm IS NULL
                   OR o.deliveryRequestDtm < :cursorDtm
                   OR (o.deliveryRequestDtm = :cursorDtm AND o.orderId < :cursorId))
            ORDER BY o.deliveryRequestDtm DESC, o.orderId DESC
            """)
    List<OrderSummaryDto> findPageByBoormiId(@Param("userId") UUID userId, @Param("orderCds") Collection<OrderCd> orderCds,
            @Param("cursorDtm") LocalDateTime cursorDtm, @Param("cursorId") UUID cursorId, Pageable pageable);

    /**
     * 활동 내역 목록의 커서 기반 페이지 조회(드리미). 나머지는 {@link #findPageByBoormiId}와 동일.
     */
    @Query("""
            SELECT new com.naengsam.quick.domain.order.dto.OrderSummaryDto(
                o.orderId, o.itemName, o.itemCd, o.orderCd, o.deliveryAmount, o.deliveryEta,
                o.deliveryDistance, o.originLatitude, o.originLongitude, o.originAlias, o.originAddressLine1,
                o.destinationLatitude, o.destinationLongitude, o.destinationAlias, o.destinationAddressLine1,
                o.imageKey, o.deliveryRequest, o.deliveryRequestDtm)
            FROM Orders o
            WHERE o.dreamiId = :userId
              AND o.orderCd IN :orderCds
              AND (:cursorDtm IS NULL
                   OR o.deliveryRequestDtm < :cursorDtm
                   OR (o.deliveryRequestDtm = :cursorDtm AND o.orderId < :cursorId))
            ORDER BY o.deliveryRequestDtm DESC, o.orderId DESC
            """)
    List<OrderSummaryDto> findPageByDreamiId(@Param("userId") UUID userId, @Param("orderCds") Collection<OrderCd> orderCds,
            @Param("cursorDtm") LocalDateTime cursorDtm, @Param("cursorId") UUID cursorId, Pageable pageable);

    /**
     * 활동 내역 화면의 상태별(전체/진행중/완료/취소) 탭 개수. 목록 페이지네이션과 별개로 화면 진입 시 한 번만 호출한다.
     */
    @Query("""
            SELECT new com.naengsam.quick.domain.order.dto.OrderStatusCountDto(o.orderCd, COUNT(o))
            FROM Orders o WHERE o.boormiId = :userId GROUP BY o.orderCd
            """)
    List<OrderStatusCountDto> countGroupedByOrderCdForBoormi(@Param("userId") UUID userId);

    /**
     * {@link #countGroupedByOrderCdForBoormi}와 동일하되 드리미 기준.
     */
    @Query("""
            SELECT new com.naengsam.quick.domain.order.dto.OrderStatusCountDto(o.orderCd, COUNT(o))
            FROM Orders o WHERE o.dreamiId = :userId GROUP BY o.orderCd
            """)
    List<OrderStatusCountDto> countGroupedByOrderCdForDreami(@Param("userId") UUID userId);
}
