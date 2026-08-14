package com.naengsam.quick.domain.matching.repository;

import com.naengsam.quick.domain.matching.entity.Matching;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRepository extends JpaRepository<Matching, UUID> {

    /**
     * 주문 하나당 매칭 성사(부르미 확정) 시점에만 행이 생기므로 최대 1개다.
     */
    Optional<Matching> findByOrderId(UUID orderId);
}
