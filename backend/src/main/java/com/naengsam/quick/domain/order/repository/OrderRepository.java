package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.Orders;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Orders, UUID> {
    @Query(value = """
            SELECT COUNT(*) FROM orders o
            WHERE o.order_cd NOT IN ('COMPLETED','CANCELLED','CLAIM_REVIEW')
              AND (o.boormi_id = :userId OR o.dreami_id = :userId)
            """, nativeQuery = true)
    long countActiveOrders(@Param("userId") UUID userId);
}
