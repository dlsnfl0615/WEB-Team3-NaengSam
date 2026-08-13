package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    // 부르미 대시보드의 "이번 달 이용 건수" 집계용. 주문에는 완료 시각이 없어서 배달 완료 시각(deliveryEndDtm)을 기준으로 센다.
    @Query("SELECT COUNT(d) FROM Delivery d "
            + "WHERE d.boormiId = :boormiId AND d.deliveryCd = com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED "
            + "AND d.deliveryEndDtm >= :start AND d.deliveryEndDtm < :end")
    long countDeliveredByBoormiBetween(@Param("boormiId") UUID boormiId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 드리미 활동 내역처럼 여러 주문의 배달 상태·완료 시각을 한 번에 조회할 때 쓰는 배치 조회(락 없음).
    List<Delivery> findAllByOrderIdIn(List<UUID> orderIds);

    // 추적 중인데 드리미 위치가 threshold 이후로 끊긴 배달들(GPS 끊김 감지용, 락 없음).
    // lastLocationDtm IS NOT NULL 조건으로 '첫 위치가 아직 안 온 배달'은 제외한다 —
    // 추적하다 끊긴 경우만 다루고, 첫 fix가 늦는 경우는 별개 문제다.
    @Query("SELECT d FROM Delivery d WHERE d.deliveryCd IN :statuses "
            + "AND d.lastLocationDtm IS NOT NULL AND d.lastLocationDtm < :threshold")
    List<Delivery> findStaleLocationDeliveries(@Param("statuses") Collection<DeliveryCd> statuses,
            @Param("threshold") LocalDateTime threshold);
}
