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

    @Column(name = "id_card_key", length = 500, nullable = false)
    private String idCardKey;

    @Column(name = "dreami_avg_score", nullable = false, columnDefinition = "DECIMAL(3,2) DEFAULT 0")
    private BigDecimal dreamiAvgScore;

    @Column(name = "criminal_record_key", length = 500, nullable = false)
    private String criminalRecordKey;

    /**
     * 드리미 인증 신청을 생성한다. {@code dreamiId} 는 부르미와 동일한 사람이므로 boormiId 를 그대로 쓴다.
     * 신청 상태는 REQUESTED 로 시작하고, 평점은 0점부터 시작한다.
     */
    public static Dreami create(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        Dreami dreami = new Dreami();
        dreami.dreamiId = dreamiId;
        dreami.requestCd = DreamiCd.REQUESTED;
        dreami.requestDtm = LocalDateTime.now();
        dreami.idCardKey = idCardKey;
        dreami.criminalRecordKey = criminalRecordKey;
        dreami.dreamiAvgScore = BigDecimal.ZERO;
        return dreami;
    }

    /**
     * 관리자가 인증 신청을 승인한다.
     */
    public void approve() {
        this.requestCd = DreamiCd.APPROVED;
        this.reviewDtm = LocalDateTime.now();
    }

    /**
     * 관리자가 인증 신청을 반려한다.
     */
    public void reject(String detail) {
        this.requestCd = DreamiCd.REJECTED;
        this.reviewDtm = LocalDateTime.now();
        this.rejectDetail = detail;
    }
}
