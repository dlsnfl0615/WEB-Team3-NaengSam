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
@Table(name = "DELIVERY_CERTIFICATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryCertification {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "certification_id", columnDefinition = "BINARY(16)")
    private UUID certificationId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryId;

    @Column(name = "is_contact", nullable = false)
    private boolean isContact;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "sign_key", length = 500)
    private String signKey;

    @Column(name = "submitted_dtm", nullable = false)
    private LocalDateTime submittedDtm;

    public static DeliveryCertification create(String imageKey, LocalDateTime submittedDtm, UUID deliveryId) {
        DeliveryCertification certification = new DeliveryCertification();
        certification.certificationId = UUID.randomUUID();
        certification.isContact = false;
        certification.imageKey = imageKey;
        certification.submittedDtm = submittedDtm;
        certification.deliveryId = deliveryId;
        return certification;
    }
}
