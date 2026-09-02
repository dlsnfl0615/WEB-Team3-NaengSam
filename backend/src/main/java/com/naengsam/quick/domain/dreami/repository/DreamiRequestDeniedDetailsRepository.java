package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.DreamiRequestDeniedDetails;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA 리포지토리. 구현 클래스 없이 인터페이스만 선언해도 Spring이 런타임에 구현체를 만들어준다.
public interface DreamiRequestDeniedDetailsRepository extends JpaRepository<DreamiRequestDeniedDetails, UUID> {

    long countByDreamiId(UUID dreamiId); // 메서드 이름만으로 "dreamiId로 개수 세기" 쿼리가 자동 생성됨
}
