package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTxRepository extends JpaRepository<PointTx, UUID> {

    // UQ_POINT_TX_ORDER_TYPE 덕분에 (order_id, type) 조합은 최대 한 행이다.
    Optional<PointTx> findByOrderIdAndType(UUID orderId, PointTxTypeCd type);

    // 환불의 check-then-act(REFUNDED_FULL 인지 확인한 뒤 전이)를 직렬화하기 위해 비관적 쓰기 락으로 조회한다
    // (트랜잭션 안에서만 사용). 락 없이 읽으면 동시 취소 두 건이 같은 PENDING 스냅샷을 보고 둘 다 전이에 성공해
    // 잔액이 두 번 복구된다(이중환불). 먼저 락을 잡은 쪽이 커밋할 때까지 나머지는 대기했다가 최신 상태를 다시 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PointTx t where t.orderId = :orderId and t.type = :type")
    Optional<PointTx> findByOrderIdAndTypeForUpdate(@Param("orderId") UUID orderId,
            @Param("type") PointTxTypeCd type);
}
