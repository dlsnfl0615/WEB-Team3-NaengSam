package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.DreamiRequestDeniedDetails;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamiRequestDeniedDetailsRepository extends JpaRepository<DreamiRequestDeniedDetails, UUID> {

    long countByDreamiId(UUID dreamiId);
}
