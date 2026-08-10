package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.dto.MoneyTransactionRow;
import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyLedgerRepository extends JpaRepository<MoneyLedger, UUID> {

    // 화면에 보여줄 거래 유형(정산/전환)이 원장이 아니라 MONEY_TX 에 있어 함께 읽는다.
    @Query("SELECT new com.naengsam.quick.domain.payment.dto.MoneyTransactionRow("
            + "tx.type, l.amount, l.balanceAfter, l.createdDtm) "
            + "FROM MoneyLedger l, MoneyTx tx "
            + "WHERE l.moneyTxId = tx.moneyTxId AND l.walletId = :walletId "
            + "ORDER BY l.createdDtm DESC")
    List<MoneyTransactionRow> findRecentByWalletId(@Param("walletId") UUID walletId, Pageable pageable);
}
