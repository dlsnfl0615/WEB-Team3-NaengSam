package com.naengsam.quick.domain.address.repository;

import com.naengsam.quick.domain.address.entity.Address;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Address} 조회용 리포지토리. {@code findAllByBoormiId} 는 호출자가 자기 배송지만 조회하도록 스코프를 강제하기 위한
 * 파생 쿼리다 — 컨트롤러가 조회 전체를 이 메서드 하나로만 노출한다.
 */
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findAllByBoormiId(UUID boormiId);
}
