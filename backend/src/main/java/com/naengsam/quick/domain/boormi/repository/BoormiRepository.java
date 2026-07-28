package com.naengsam.quick.domain.boormi.repository;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoormiRepository extends JpaRepository<Boormi, UUID> {

    Optional<Boormi> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
