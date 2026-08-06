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
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID dreamiId;
}
