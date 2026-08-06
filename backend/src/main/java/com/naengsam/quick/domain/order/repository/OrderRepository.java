package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Orders, UUID> {

    /**
     * 드리미가 지금 수행 중인 배달 건을 찾는다. 드리미는 한 번에 하나만 수행하므로 단건 조회다.
     */
    Optional<Orders> findByDreamiIdAndOrderCd(UUID dreamiId, OrderCd orderCd);

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
     * 부르미 주문 목록 첫 페이지. 최신순(delivery_request_dtm DESC, 동일 시각은 order_id DESC) 정렬. status 가 null 이면 전체 상태를 조회한다.
     */
    @Query(value = """
            SELECT * FROM ORDERS o
            WHERE o.boormi_id = :boormiId
              AND (:status IS NULL OR o.order_cd = :status)
            ORDER BY o.delivery_request_dtm DESC, o.order_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Orders> findFirstPageByBoormi(@Param("boormiId") UUID boormiId,
                                       @Param("status") String status, @Param("limit") int limit);

    /**
     * 부르미 주문 목록 커서 이후 페이지. keyset 조건으로 (dtm, order_id) 가 커서보다 작은 행만 이어서 조회한다. status 가 null 이면 전체 상태를 조회한다.
     */
    @Query(value = """
            SELECT * FROM ORDERS o
            WHERE o.boormi_id = :boormiId
              AND (:status IS NULL OR o.order_cd = :status)
              AND (o.delivery_request_dtm < :cursorDtm
                   OR (o.delivery_request_dtm = :cursorDtm AND o.order_id < :cursorId))
            ORDER BY o.delivery_request_dtm DESC, o.order_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Orders> findPageByBoormiAfterCursor(@Param("boormiId") UUID boormiId,
                                             @Param("status") String status,
                                             @Param("cursorDtm") LocalDateTime cursorDtm,
                                             @Param("cursorId") UUID cursorId, @Param("limit") int limit);
}
