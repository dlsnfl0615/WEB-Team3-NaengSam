package com.naengsam.quick.domain.dreami.entity;

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

    public static DreamiReview create(UUID orderId, int score, String content) {
        DreamiReview review = new DreamiReview();
        review.reviewId = UUID.randomUUID();
        review.orderId = orderId;
        review.score = score;
        review.content = content;
        review.createdDtm = LocalDateTime.now();
        return review;
    }
}
