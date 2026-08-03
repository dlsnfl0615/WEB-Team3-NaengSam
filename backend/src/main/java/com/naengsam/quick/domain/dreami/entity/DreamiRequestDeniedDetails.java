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
@Table(name = "DREAMI_REQUEST_DENIED_DETAILS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DreamiRequestDeniedDetails {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "reject_id", columnDefinition = "BINARY(16)")
    private UUID rejectId;

    @Column(name = "rejected_dtm", nullable = false)
    private LocalDateTime rejectedDtm;

    @Column(name = "reject_detail", length = 200)
    private String rejectDetail;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID dreamiId;

    public static DreamiRequestDeniedDetails create(UUID dreamiId, String rejectDetail) {
        DreamiRequestDeniedDetails details = new DreamiRequestDeniedDetails();
        details.rejectId = UUID.randomUUID();
        details.rejectedDtm = LocalDateTime.now();
        details.rejectDetail = rejectDetail;
        details.dreamiId = dreamiId;
        return details;
    }
}
