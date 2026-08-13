package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    @Query(value = """
            SELECT COUNT(*) FROM ORDERS o
            WHERE o.order_cd NOT IN ('COMPLETED','CANCELLED','CLAIM_REVIEW')
              AND (o.boormi_id = :userId OR o.dreami_id = :userId)
            """, nativeQuery = true)
    long countActiveOrders(@Param("userId") UUID userId);

    /**
     * 주문 목록 첫 페이지. role이 BOORMI면 boormi_id, DREAMI면 dreami_id로 필터링한다. 최신순(delivery_request_dtm DESC, 동일 시각은
     * order_id DESC) 정렬. status 가 null 이면 전체 상태를 조회한다.
     */
    @Query(value = """
            SELECT * FROM ORDERS o
            WHERE ((:role = 'BOORMI' AND o.boormi_id = :userId)
                OR (:role = 'DREAMI' AND o.dreami_id = :userId))
              AND (:status IS NULL OR o.order_cd = :status)
            ORDER BY o.delivery_request_dtm DESC, o.order_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Orders> findFirstPageByRole(@Param("userId") UUID userId, @Param("role") String role,
                                     @Param("status") String status, @Param("limit") int limit);

    /**
     * 주문 목록 커서 이후 페이지. keyset 조건으로 (dtm, order_id) 가 커서보다 작은 행만 이어서 조회한다. status 가 null 이면 전체 상태를 조회한다.
     */
    @Query(value = """
            SELECT * FROM ORDERS o
            WHERE ((:role = 'BOORMI' AND o.boormi_id = :userId)
                OR (:role = 'DREAMI' AND o.dreami_id = :userId))
              AND (:status IS NULL OR o.order_cd = :status)
              AND (o.delivery_request_dtm < :cursorDtm
                   OR (o.delivery_request_dtm = :cursorDtm AND o.order_id < :cursorId))
            ORDER BY o.delivery_request_dtm DESC, o.order_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Orders> findPageByRoleAfterCursor(@Param("userId") UUID userId, @Param("role") String role,
                                           @Param("status") String status,
                                           @Param("cursorDtm") LocalDateTime cursorDtm,
                                           @Param("cursorId") UUID cursorId, @Param("limit") int limit);
}
