package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.SettlementDetails;
import com.naengsam.quick.domain.payment.entity.SettlementDetailsId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementDetailsRepository extends JpaRepository<SettlementDetails, SettlementDetailsId> {
}
