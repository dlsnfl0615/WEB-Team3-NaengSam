package com.naengsam.quick.domain.address.repository;

import com.naengsam.quick.domain.address.entity.Address;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findAllByBoormiId(UUID boormiId);
}
