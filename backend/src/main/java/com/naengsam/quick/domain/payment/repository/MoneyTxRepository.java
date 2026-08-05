package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyTxRepository extends JpaRepository<MoneyTx, UUID> {

    @Query("SELECT COALESCE(SUM(mt.amount), 0L) FROM MoneyTx mt, Wallet w "
            + "WHERE mt.walletId = w.walletId AND w.dreamiId = :dreamiId AND mt.type = :type "
            + "AND mt.createdDtm >= :start AND mt.createdDtm < :end")
    long sumAmountByDreamiIdAndTypeBetween(@Param("dreamiId") UUID dreamiId, @Param("type") MoneyTxType type,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(mt) FROM MoneyTx mt, Wallet w "
            + "WHERE mt.walletId = w.walletId AND w.dreamiId = :dreamiId AND mt.type = :type "
            + "AND mt.createdDtm >= :start AND mt.createdDtm < :end")
    long countByDreamiIdAndTypeBetween(@Param("dreamiId") UUID dreamiId, @Param("type") MoneyTxType type,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
