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
 * 드리미가 받은 리뷰. 부르미가 작성한다. 리뷰 대상 드리미는 주문(ORDERS)의 dreami_id 로 역추적한다.
 */
@Entity
@Table(name = "DREAMI_REVIEW")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DreamiReview {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "review_id", columnDefinition = "BINARY(16)")
    private UUID reviewId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "content", length = 200)
    private String content;

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
     * 별점만 먼저 남긴다. 리뷰 내용은 나중에 {@link #updateContent(String)} 로 채운다.
     */
    public static DreamiReview create(UUID orderId, int score) {
        DreamiReview review = new DreamiReview();
        review.reviewId = UUID.randomUUID();
        review.orderId = orderId;
        review.score = score;
        review.createdDtm = LocalDateTime.now();
        return review;
    }

    /**
     * 리뷰 내용을 채우거나 수정한다.
     */
    public void updateContent(String content) {
        this.content = content;
        this.updatedDtm = LocalDateTime.now();
    }
}
