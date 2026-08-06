package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointLedger;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, UUID> {
}
