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
 * 포인트 지갑. 모든 회원이 갖는 일반 지갑으로, PG 로 충전한 포인트를 담고 배달 콜을 잡을 때 결제로 차감된다. PK 는 {@link Wallet} 의 wallet_id 를 그대로 쓴다.
 */
@Entity
@Table(name = "POINT_WALLET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)")
    private UUID walletId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "updated_dtm", nullable = false)
    private LocalDateTime updatedDtm;

    public static PointWallet create(UUID walletId) {
        PointWallet pointWallet = new PointWallet();
        pointWallet.walletId = walletId;
        pointWallet.amount = 0L;
        pointWallet.updatedDtm = LocalDateTime.now();
        return pointWallet;
    }
}
