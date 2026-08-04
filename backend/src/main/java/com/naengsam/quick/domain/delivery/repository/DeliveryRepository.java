package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    // 주문 단위 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findByOrderId(UUID orderId);
}
