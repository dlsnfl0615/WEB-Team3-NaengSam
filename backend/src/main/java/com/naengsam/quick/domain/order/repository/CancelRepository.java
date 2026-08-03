package com.naengsam.quick.domain.order.repository;

import com.naengsam.quick.domain.order.entity.Cancel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancelRepository extends JpaRepository<Cancel, UUID> {
}
