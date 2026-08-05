package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointWallet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {
}
