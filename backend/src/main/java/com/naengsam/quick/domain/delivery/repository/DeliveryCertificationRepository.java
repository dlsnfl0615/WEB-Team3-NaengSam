package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.DeliveryCertification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryCertificationRepository extends JpaRepository<DeliveryCertification, UUID> {
}
