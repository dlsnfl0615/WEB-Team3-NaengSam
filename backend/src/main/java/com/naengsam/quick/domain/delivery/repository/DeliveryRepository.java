package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    // 주문 단위 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 조회 후에 DeliveryCd를 확인하고, 상태 전이 가능하면 update 후 save하기 때문
    // DB에 행 단위 락을 걸고 있음 (order_id에 인덱스 걸려있음)
    // 주의: readOnly 트랜잭션에서 호출하면 "FOR UPDATE" 때문에 실패한다 — 단순 조회는 findByOrderIdWithoutLock을 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findByOrderId(UUID orderId);

    // 락 없는 단순 조회(상세 조회 등 readOnly 트랜잭션용).
    @Query("SELECT d FROM Delivery d WHERE d.orderId = :orderId")
    Optional<Delivery> findByOrderIdWithoutLock(@Param("orderId") UUID orderId);

    // 홈 화면의 "오늘의 완료 건수" 집계용. deliveryEndDtm은 markDelivered 시점에만 채워진다.
    // start 이상, end 미만(상한 배타)으로 MoneyTxRepository의 오늘 수익 집계와 "오늘" 경계를 동일하게 맞춘다.
    @Query("SELECT COUNT(d) FROM Delivery d "
            + "WHERE d.dreamiId = :dreamiId AND d.deliveryCd = com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED "
            + "AND d.deliveryEndDtm >= :start AND d.deliveryEndDtm < :end")
    long countDeliveredBetween(@Param("dreamiId") UUID dreamiId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
