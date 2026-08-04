package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    // 주문 단위 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 조회 후에 DeliveryCd를 확인하고, 상태 전이 가능하면 update 후 save하기 때문
    // DB에 행 단위 락을 걸고 있음 (order_id에 인덱스 걸려있음)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findByOrderId(UUID orderId);
}
