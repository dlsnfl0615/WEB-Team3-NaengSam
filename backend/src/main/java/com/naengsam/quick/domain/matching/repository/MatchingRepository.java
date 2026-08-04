package com.naengsam.quick.domain.matching.repository;

import com.naengsam.quick.domain.matching.entity.Matching;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRepository extends JpaRepository<Matching, UUID> {
}
