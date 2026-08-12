package com.naengsam.quick.domain.order.entity;

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
 * 부르미가 받은 리뷰. 드리미가 작성한다. 리뷰 대상 부르미는 주문(ORDERS)의 boormi_id 로 역추적한다. 본문 컬럼명이 DREAMI_REVIEW 와 달리 detail 이다(DDL 기준).
 */
@Entity
@Table(name = "BOORMI_REVIEW")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoormiReview {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "review_id", columnDefinition = "BINARY(16)")
    private UUID reviewId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "detail", length = 200)
    private String detail;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @Column(name = "updated_dtm")
    private LocalDateTime updatedDtm;

    @Column(name = "deleted_dtm")
    private LocalDateTime deletedDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    /**
     * 별점만 먼저 남긴다. 리뷰 내용은 나중에 {@link #updateDetail(String)} 로 채운다.
     */
    public static BoormiReview create(UUID orderId, int score) {
        BoormiReview review = new BoormiReview();
        review.reviewId = UUID.randomUUID();
        review.orderId = orderId;
        review.score = score;
        review.createdDtm = LocalDateTime.now();
        return review;
    }

    /**
     * 리뷰 내용을 채우거나 수정한다.
     */
    public void updateDetail(String detail) {
        this.detail = detail;
        this.updatedDtm = LocalDateTime.now();
    }
}
