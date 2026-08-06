package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    // 서비스가 아는 건 boormi_id 뿐이라 지갑을 찾는 진입점이 된다(UQ_WALLET_BOORMI 로 한 회원당 한 행이 보장된다).
    Optional<Wallet> findByBoormiId(UUID boormiId);
}
