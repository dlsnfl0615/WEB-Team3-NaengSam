package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DreamiService {

    private final DreamiRepository dreamiRepository;
    private final BoormiRepository boormiRepository;
    private final DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;

    @Transactional
    public void saveVerificationFileKeys(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        dreamiRepository.save(Dreami.create(dreamiId, idCardKey, criminalRecordKey));
    }

    /**
     * 부르미가 드리미 프로필을 조회한다. dreamiId 는 boormiId 와 동일한 값이므로 이름은 BOORMI 테이블에서 가져온다.
     */
    @Transactional(readOnly = true)
    public DreamiProfileDto getDreamiProfile(UUID dreamiId) {
        Dreami dreami = dreamiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        Boormi boormi = boormiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        long rejectCount = dreamiRequestDeniedDetailsRepository.countByDreamiId(dreamiId);

        return DreamiProfileDto.from(dreami, boormi.getName(), rejectCount);
    }
}
