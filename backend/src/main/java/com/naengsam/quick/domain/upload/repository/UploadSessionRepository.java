package com.naengsam.quick.domain.upload.repository;

import com.naengsam.quick.domain.upload.entity.UploadSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {  // Spring Data JPA: 인터페이스 선언만으로 CRUD 자동 구현. <엔티티 타입, PK 타입>

    Optional<UploadSession> findByS3Key(String s3Key);  // 파생 쿼리(메서드명으로 SQL 자동 생성). Optional로 감싸 null 대신 비어있는 컨테이너 반환

    /**
     * status가 ISSUED일 때만 CONSUMED로 원자적으로 전이시킨다. 동시에 같은 key로 호출돼도 이 조건부 UPDATE 자체가
     * DB row-lock으로 원자적이라, 딱 한 트랜잭션만 1을 받고 나머지는 0을 받는다.
     *
     * @return 이번 호출로 전이시켰으면 1, 이미 CONSUMED거나 존재하지 않으면 0
     */
    @Modifying  // SELECT가 아닌 UPDATE/DELETE/INSERT 쿼리임을 선언. 없으면 Spring Data가 읽기 전용으로 처리해 예외 발생 → annotations.md
    @Query("""
            UPDATE UploadSession s
            SET s.status = com.naengsam.quick.domain.upload.entity.UploadSessionCd.CONSUMED,
                s.consumedDtm = CURRENT_TIMESTAMP
            WHERE s.s3Key = :s3Key
              AND s.status = com.naengsam.quick.domain.upload.entity.UploadSessionCd.ISSUED
            """)  // 텍스트 블록(Java 15+). """ 로 감싸 여러 줄 문자열을 들여쓰기 유지하며 작성 → java-patterns.md
    int markConsumedIfIssued(@Param("s3Key") String s3Key);  // @Param: JPQL의 :s3Key 파라미터와 메서드 파라미터를 이름으로 연결
}
