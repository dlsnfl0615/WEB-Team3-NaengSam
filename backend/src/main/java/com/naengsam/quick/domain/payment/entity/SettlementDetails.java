package com.naengsam.quick.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 드리미가 등록한 정산 계좌. 머니 지갑의 돈을 실제로 출금할 때 입금받는 계좌 정보다.
 */
@Entity
@Table(name = "SETTLEMENT_DETAILS")
@IdClass(SettlementDetailsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementDetails {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "settlement_id", columnDefinition = "BINARY(16)")
    private UUID settlementId;

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)")
    private UUID dreamiId;

    @Column(name = "settlement_account", length = 50, nullable = false)
    private String settlementAccount;

    @Column(name = "settlement_bank", length = 50, nullable = false)
    private String settlementBank;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Column(name = "registered_dtm", nullable = false)
    private LocalDateTime registeredDtm;

    public static SettlementDetails create(UUID dreamiId, String settlementBank,
            String settlementAccount, String accountHolder) {
        SettlementDetails settlementDetails = new SettlementDetails();
        settlementDetails.settlementId = UUID.randomUUID();
        settlementDetails.dreamiId = dreamiId;
        settlementDetails.settlementBank = settlementBank;
        settlementDetails.settlementAccount = settlementAccount;
        settlementDetails.accountHolder = accountHolder;
        settlementDetails.registeredDtm = LocalDateTime.now();
        return settlementDetails;
    }
}
