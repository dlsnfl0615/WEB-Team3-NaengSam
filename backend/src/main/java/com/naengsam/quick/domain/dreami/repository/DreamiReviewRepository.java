package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.DreamiReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamiReviewRepository extends JpaRepository<DreamiReview, UUID> {

    // 드리미 활동 내역처럼 여러 주문의 평점을 한 번에 조회할 때 쓰는 배치 조회.
    List<DreamiReview> findAllByOrderIdIn(List<UUID> orderIds);
}
