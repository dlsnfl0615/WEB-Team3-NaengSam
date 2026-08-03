package com.naengsam.quick.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "CANCEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cancel {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "cancel_id", columnDefinition = "BINARY(16)")
    private UUID cancelId;

    // 주문당 취소 이력은 1건만 존재해야 한다. 동시 취소(더블클릭/재시도) 중복 삽입 방지를 위해 DB에 UNIQUE(order_id) 제약 필요.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canceler_cd", nullable = false)
    private CancelerCd cancelerCd;

    @Column(name = "cancel_reason", length = 50)
    private String cancelReason;

    @Column(name = "note")
    private String note;

    @Column(name = "is_penalty_applied", nullable = false)
    private boolean isPenaltyApplied;

    @Column(name = "canceled_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime canceledDtm;

    /**
     * 주문 취소 이력을 생성한다. PK 는 앱에서 생성(BINARY(16))하며 {@code canceled_dtm} 은 DB 기본값(CURRENT_TIMESTAMP)이 적용된다.
     */
    public static Cancel create(UUID orderId, CancelerCd cancelerCd, boolean isPenaltyApplied) {
        Cancel cancel = new Cancel();
        cancel.cancelId = UUID.randomUUID();
        cancel.orderId = orderId;
        cancel.cancelerCd = cancelerCd;
        cancel.isPenaltyApplied = isPenaltyApplied;
        return cancel;
    }
}
