package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.MoneyTx;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyTxRepository extends JpaRepository<MoneyTx, UUID> {
}
