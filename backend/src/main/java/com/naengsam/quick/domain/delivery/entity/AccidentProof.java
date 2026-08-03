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
@Table(name = "ACCIDENT_PROOF")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccidentProof {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "proof_id", columnDefinition = "BINARY(16)")
    private UUID proofId;

    @Column(name = "proof_type", length = 200)
    private String proofType;

    @Column(name = "file_key", length = 500)
    private String fileKey;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "accident_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID accidentId;
}
