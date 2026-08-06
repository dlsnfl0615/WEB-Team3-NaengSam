package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyLedgerRepository extends JpaRepository<MoneyLedger, UUID> {
}
