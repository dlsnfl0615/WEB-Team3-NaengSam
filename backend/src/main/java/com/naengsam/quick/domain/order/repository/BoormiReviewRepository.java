package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.BoormiReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoormiReviewRepository extends JpaRepository<BoormiReview, UUID> {

    Optional<BoormiReview> findByOrderId(UUID orderId);

    // 부르미 활동 내역처럼 여러 주문의 평점을 한 번에 조회할 때 쓰는 배치 조회.
    List<BoormiReview> findAllByOrderIdIn(List<UUID> orderIds);

    /**
     * 리뷰 테이블에 대상자 컬럼이 없으므로 ORDERS 와 조인해 해당 부르미가 받은 리뷰의 평균 별점을 구한다. 리뷰가 하나도 없으면 null 을 반환한다.
     */
    @Query("SELECT AVG(r.score) FROM BoormiReview r, Orders o "
            + "WHERE r.orderId = o.orderId AND o.boormiId = :boormiId AND r.deletedDtm IS NULL")
    Double findAvgScoreByBoormiId(@Param("boormiId") UUID boormiId);
}
