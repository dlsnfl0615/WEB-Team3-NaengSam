package com.naengsam.quick.domain.matching.entity;

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

@Entity
@Table(name = "MATCHING")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Matching {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "matching_id", columnDefinition = "BINARY(16)")
    private UUID matchingId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @Column(name = "accepted_dtm")
    private LocalDateTime acceptedDtm;

    @Column(name = "canceled_dtm")
    private LocalDateTime canceledDtm;

    @Column(name = "created_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdDtm;

    /**
     * 주문에 대한 매칭 레코드를 생성한다. PK 는 앱에서 생성(BINARY(16))하며,
     * {@code created_dtm} 은 DB 기본값(CURRENT_TIMESTAMP)이 적용된다. 수락/취소 시각은 이후 상태 전이로 채워진다.
     */
    public static Matching create(UUID orderId) {
        Matching matching = new Matching();
        matching.matchingId = UUID.randomUUID();
        matching.orderId = orderId;
        return matching;
    }

    /**
     * 매칭 성사(부르미·드리미 확정) 시각을 기록한다. dirty checking 으로 accepted_dtm 컬럼에 반영된다.
     */
    public void markAccepted() {
        this.acceptedDtm = LocalDateTime.now();
    }

    /**
     * 매칭 취소 시각을 기록한다. dirty checking 으로 canceled_dtm 컬럼에 반영된다.
     */
    public void markCanceled() {
        this.canceledDtm = LocalDateTime.now();
    }
}
