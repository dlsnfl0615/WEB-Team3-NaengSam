package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyWalletRepository extends JpaRepository<MoneyWallet, UUID> {

    // 잔액 check-then-act(잔액 확인 후 차감)를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from MoneyWallet w where w.walletId = :walletId")
    Optional<MoneyWallet> findByIdForUpdate(@Param("walletId") UUID walletId);
}
