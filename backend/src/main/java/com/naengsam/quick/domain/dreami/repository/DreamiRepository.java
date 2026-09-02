package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// JpaRepository<Dreami, UUID>: Spring Data JPA가 제공하는 인터페이스. save/findById/delete 같은 기본 CRUD를
// 구현체 없이 그냥 상속만으로 쓸 수 있게 해준다. 아래 findByDreamiId, findAllByRequestCd 처럼 메서드 이름을
// 규칙대로 지으면("findBy필드명", "findAllBy필드명") Spring이 이름을 해석해 쿼리를 자동으로 만들어준다(직접 SQL/JPQL 안 써도 됨).
public interface DreamiRepository extends JpaRepository<Dreami, UUID> {

    // 인증 신청 재제출 check-then-act를 직렬화하기 위해 비관적 쓰기 락으로 조회한다(트랜잭션 안에서만 사용).
    // 조회 후에 requestCd를 확인하고, APPROVED가 아니면 그대로 덮어써서 저장하기 때문
    // DB에 행 단위 락을 걸고 있음(dreami_id가 PK)
    @Lock(LockModeType.PESSIMISTIC_WRITE) // 이 조회로 가져온 행에 DB row lock을 걸어, 트랜잭션이 끝날 때까지 다른 트랜잭션이 같은 행을 못 건드리게 막는다
    Optional<Dreami> findByDreamiId(UUID dreamiId); // Optional<T>: 값이 없을 수도 있음을 타입으로 표현. null 대신 반환해서 호출부가 존재 여부를 명시적으로 처리하게 강제

    List<Dreami> findAllByRequestCd(DreamiCd requestCd);
}
