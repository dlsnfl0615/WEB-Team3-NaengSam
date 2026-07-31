package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DreamiService {

    private final DreamiRepository dreamiRepository;

    @Transactional
    public void saveVerificationFileKeys(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        dreamiRepository.save(Dreami.create(dreamiId, idCardKey, criminalRecordKey));
    }
}
