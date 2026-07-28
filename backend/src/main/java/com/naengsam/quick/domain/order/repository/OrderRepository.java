package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.Orders;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Orders, UUID> {
}
