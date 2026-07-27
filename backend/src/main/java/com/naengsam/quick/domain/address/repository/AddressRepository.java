package com.naengsam.quick.domain.address.repository;

import com.naengsam.quick.domain.order.entity.Orders;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Orders, UUID> {
}
