package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.Wallet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
}
