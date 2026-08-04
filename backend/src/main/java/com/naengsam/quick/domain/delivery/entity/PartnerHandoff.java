package com.naengsam.quick.domain.delivery.entity;

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
@Table(name = "PARTNER_HANDOFF")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerHandoff {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "partner_handoff_id", columnDefinition = "BINARY(16)")
    private UUID partnerHandoffId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "partner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID partnerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "accident_id", columnDefinition = "BINARY(16)")
    private UUID accidentId;

    @Column(name = "handoff_dtm", nullable = false)
    private LocalDateTime handoffDtm;

    @Column(name = "is_picked_up", nullable = false)
    private boolean isPickedUp;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "handoff_reason", length = 50)
    private String handoffReason;

    @Column(name = "picked_up_dtm")
    private LocalDateTime pickedUpDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;
}
