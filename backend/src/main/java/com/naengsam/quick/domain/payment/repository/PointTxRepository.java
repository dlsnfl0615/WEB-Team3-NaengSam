package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointTx;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTxRepository extends JpaRepository<PointTx, UUID> {
}
