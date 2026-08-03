package com.naengsam.quick.domain.upload.repository;

import com.naengsam.quick.domain.upload.entity.UploadSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    Optional<UploadSession> findByS3Key(String s3Key);

    /**
     * status가 ISSUED일 때만 CONSUMED로 원자적으로 전이시킨다. 동시에 같은 key로 호출돼도 이 조건부 UPDATE 자체가
     * DB row-lock으로 원자적이라, 딱 한 트랜잭션만 1을 받고 나머지는 0을 받는다.
     *
     * @return 이번 호출로 전이시켰으면 1, 이미 CONSUMED거나 존재하지 않으면 0
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UploadSession s
            SET s.status = com.naengsam.quick.domain.upload.entity.UploadSessionCd.CONSUMED,
                s.consumedDtm = CURRENT_TIMESTAMP
            WHERE s.s3Key = :s3Key
              AND s.status = com.naengsam.quick.domain.upload.entity.UploadSessionCd.ISSUED
            """)
    int markConsumedIfIssued(@Param("s3Key") String s3Key);
}
