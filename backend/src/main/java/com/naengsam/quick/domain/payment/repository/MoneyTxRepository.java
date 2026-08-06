package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.dto.MonthlyMoneyAggregate;
import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyTxRepository extends JpaRepository<MoneyTx, UUID> {

    @Query("SELECT new com.naengsam.quick.domain.payment.dto.MonthlyMoneyAggregate("
            + "YEAR(mt.createdDtm), MONTH(mt.createdDtm), SUM(mt.amount), COUNT(mt)) "
            + "FROM MoneyTx mt, Wallet w "
            + "WHERE mt.walletId = w.walletId AND w.boormiId = :boormiId AND mt.type = :type "
            + "AND mt.createdDtm >= :start AND mt.createdDtm < :end "
            + "GROUP BY YEAR(mt.createdDtm), MONTH(mt.createdDtm)")
    List<MonthlyMoneyAggregate> aggregateByBoormiIdAndTypeBetween(
            @Param("boormiId") UUID boormiId, @Param("type") MoneyTxTypeCd type,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
