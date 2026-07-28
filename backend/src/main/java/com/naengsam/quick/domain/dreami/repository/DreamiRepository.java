package com.naengsam.quick.domain.dreami.repository;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamiRepository extends JpaRepository<Dreami, UUID> {
}
