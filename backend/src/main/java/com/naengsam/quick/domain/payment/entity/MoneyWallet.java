package com.naengsam.quick.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 머니 지갑. 드리미 지위를 얻은 회원만 갖는다. 배달을 수행해 번 돈을 담고 출금·환전의 출발점이 된다. 정산 확정 전 금액은 {@code pending_amount} 에 잡힌다. PK 는 {@link Wallet} 의
 * wallet_id 를 그대로 쓴다.
 */
@Entity
@Table(name = "MONEY_WALLET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyWallet {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)")
    private UUID walletId;

    @Column(name = "pending_amount", nullable = false)
    private Long pendingAmount;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "updated_dtm", nullable = false)
    private LocalDateTime updatedDtm;

    public static MoneyWallet create(UUID walletId) {
        MoneyWallet moneyWallet = new MoneyWallet();
        moneyWallet.walletId = walletId;
        moneyWallet.pendingAmount = 0L;
        moneyWallet.amount = 0L;
        moneyWallet.updatedDtm = LocalDateTime.now();
        return moneyWallet;
    }
}
