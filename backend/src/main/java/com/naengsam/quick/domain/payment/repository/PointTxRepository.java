package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTxRepository extends JpaRepository<PointTx, UUID> {

    // UQ_POINT_TX_ORDER_TYPE 덕분에 (order_id, type) 조합은 최대 한 행이다.
    Optional<PointTx> findByOrderIdAndType(UUID orderId, PointTxTypeCd type);
}
