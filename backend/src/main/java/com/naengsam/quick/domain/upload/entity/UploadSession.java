package com.naengsam.quick.domain.upload.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * presigned URL 발급 1건을 추적하는 세션. key에 새겨진 용도(purpose)/대상(resourceId)과 실제 요청이 일치하는지 확인하는 데 쓰이고, 소비(consume) 여부를 상태로 관리해
 * 재시도로 인한 중복 처리를 막는다.
 */
@Entity
@Table(name = "UPLOAD_SESSION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadSession {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "upload_session_id", columnDefinition = "BINARY(16)")
    private UUID uploadSessionId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 50, nullable = false)
    private UploadPurpose purpose;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resource_id", columnDefinition = "BINARY(16)")
    private UUID resourceId;

    @Column(name = "s3_key", length = 500, nullable = false, unique = true)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadSessionCd status;

    @Column(name = "issued_dtm", nullable = false)
    private LocalDateTime issuedDtm;

    @Column(name = "consumed_dtm")
    private LocalDateTime consumedDtm;

    public static UploadSession create(UploadPurpose purpose, UUID boormiId, UUID resourceId, String s3Key) {
        UploadSession session = new UploadSession();
        session.uploadSessionId = UUID.randomUUID();
        session.boormiId = boormiId;
        session.purpose = purpose;
        session.resourceId = resourceId; // 어떤 것과 연결되어 있는가 (주문과 연결되어 있음)
        session.s3Key = s3Key;
        session.status = UploadSessionCd.ISSUED;
        session.issuedDtm = LocalDateTime.now();
        return session;
    }

    public boolean matches(UploadPurpose expectedPurpose, UUID expectedBoormiId, UUID expectedResourceId) {
        return purpose == expectedPurpose
                && boormiId.equals(expectedBoormiId)
                && Objects.equals(resourceId, expectedResourceId);
    }

    /**
     * @return 이번 호출로 새로 소비됐으면 true, 이미 소비된 상태(재시도)라 아무것도 하지 않았으면 false
     */
    public boolean consume() {
        if (status == UploadSessionCd.CONSUMED) {
            return false;
        }
        status = UploadSessionCd.CONSUMED;
        consumedDtm = LocalDateTime.now();
        return true;
    }
}
