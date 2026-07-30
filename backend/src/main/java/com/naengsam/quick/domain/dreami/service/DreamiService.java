package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DreamiService {

    private final DreamiRepository dreamiRepository;

    public void registerDreami(UUID dreamiId, String downloadUrl) {
//        dreamiRepository.save(new Dreami(dreamiId, null, null, null, DreamiCd.REVIEWING, downloadUrl, 0, ))
    }
}
