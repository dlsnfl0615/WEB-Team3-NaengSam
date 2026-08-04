package com.naengsam.quick.domain.delivery.repository;

import com.naengsam.quick.domain.delivery.entity.PickupCertification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupCertificationRepository extends JpaRepository<PickupCertification, UUID> {
}
