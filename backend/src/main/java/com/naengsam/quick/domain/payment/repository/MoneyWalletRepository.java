package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyWalletRepository extends JpaRepository<MoneyWallet, UUID> {
}
