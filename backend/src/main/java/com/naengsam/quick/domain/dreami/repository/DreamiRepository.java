package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DreamiRepository extends JpaRepository<Dreami, UUID> {

    // 인증 신청 재제출 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 조회 후에 requestCd를 확인하고, APPROVED가 아니면 그대로 덮어써서 저장하기 때문
    // DB에 행 단위 락을 걸고 있음(dreami_id가 PK)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Dreami> findByDreamiId(UUID dreamiId);

    List<Dreami> findAllByRequestCd(DreamiCd requestCd);
}
