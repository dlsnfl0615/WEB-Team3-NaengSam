package com.naengsam.quick.domain.upload.repository;

import com.naengsam.quick.domain.upload.entity.UploadSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    Optional<UploadSession> findByS3Key(String s3Key);
}
