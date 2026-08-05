package com.naengsam.quick.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원의 지갑. 모든 회원이 하나씩 갖는다. 이 wallet_id 를 공유 PK 로 {@link PointWallet}(항상 생성)과 {@link MoneyWallet}(드리미 활성화 시 생성)이 붙는다.
 */
@Entity
@Table(name = "WALLET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)")
    private UUID walletId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    public static Wallet create(UUID boormiId) {
        Wallet wallet = new Wallet();
        wallet.walletId = UUID.randomUUID();
        wallet.boormiId = boormiId;
        return wallet;
    }
}
