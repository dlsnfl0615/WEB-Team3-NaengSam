package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PointWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {

    // 잔액 check-then-act(잔액 확인 후 차감)를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 락 없이 조회하면 동시 결제 두 건이 같은 잔액을 읽어 둘 다 통과시킬 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PointWallet w where w.walletId = :walletId")
    Optional<PointWallet> findByIdForUpdate(@Param("walletId") UUID walletId);
}
