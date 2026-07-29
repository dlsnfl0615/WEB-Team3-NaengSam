package com.naengsam.quick.domain.dreami.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "DREAMI")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dreami {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)")
    private UUID dreamiId;

    @Column(name = "reject_detail", length = 255)
    private String rejectDetail;

    @Column(name = "request_dtm")
    private LocalDateTime requestDtm;

    @Column(name = "review_dtm")
    private LocalDateTime reviewDtm;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_cd", nullable = false)
    private DreamiCd requestCd;

    @Column(name = "id_card_url", length = 500, nullable = false)
    private String idCardUrl;

    @Column(name = "dreami_avg_score", nullable = false, columnDefinition = "DECIMAL(3,2) DEFAULT 0")
    private BigDecimal dreamiAvgScore;

    @Column(name = "criminal_record_url", length = 500, nullable = false)
    private String criminalRecordUrl;
}
