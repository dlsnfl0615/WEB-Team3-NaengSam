package com.naengsam.quick.domain.payment.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * {@link SettlementDetails} 의 복합 PK (settlement_id, dreami_id). JPA ID 클래스는 기본 생성자와 equals/hashCode 가 필요해 record 로 만들 수
 * 없다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class SettlementDetailsId implements Serializable {

    private UUID settlementId;
    private UUID dreamiId;
}
