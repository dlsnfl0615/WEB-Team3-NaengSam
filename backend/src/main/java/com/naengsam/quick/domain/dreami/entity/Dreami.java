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

@Entity // 이 클래스가 DB 테이블과 매핑되는 JPA 엔티티임을 표시(Hibernate가 관리)
@Table(name = "DREAMI")
@Getter // Lombok: 모든 필드의 getter(getDreamiId() 등)를 컴파일 시점에 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Lombok: JPA가 필요로 하는 파라미터 없는 생성자를 protected로 자동 생성(외부에서 new Dreami() 직접 호출 금지, 아래 create()만 쓰도록 강제)
public class Dreami {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY) // Hibernate 전용 annotation: UUID를 DB에는 BINARY(16) 이진값으로 저장하라는 지시
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)")
    private UUID dreamiId;

    @Column(name = "reject_detail", length = 255)
    private String rejectDetail;

    @Column(name = "request_dtm")
    private LocalDateTime requestDtm;

    @Column(name = "review_dtm")
    private LocalDateTime reviewDtm;

    @Enumerated(EnumType.STRING) // enum을 숫자(ordinal)가 아니라 "REQUESTED" 같은 문자열로 DB에 저장
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
     * 부르미가 남긴 리뷰를 반영해 드리미 평균 평점을 갱신한다.
     */
    public void updateAvgScore(BigDecimal avgScore) {
        this.dreamiAvgScore = avgScore;
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
