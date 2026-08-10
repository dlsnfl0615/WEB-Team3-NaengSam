package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.dto.PointTransactionRow;
import com.naengsam.quick.domain.payment.entity.PointLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointLedgerRepository extends JpaRepository<PointLedger, UUID> {

    // 화면에 보여줄 거래 유형(충전/결제/전환)이 원장이 아니라 POINT_TX 에 있어 함께 읽는다.
    @Query("SELECT new com.naengsam.quick.domain.payment.dto.PointTransactionRow("
            + "tx.type, l.amount, l.balanceAfter, l.createdDtm) "
            + "FROM PointLedger l, PointTx tx "
            + "WHERE l.pointTxId = tx.pointTxId AND l.walletId = :walletId "
            + "ORDER BY l.createdDtm DESC")
    List<PointTransactionRow> findRecentByWalletId(@Param("walletId") UUID walletId, Pageable pageable);
}
