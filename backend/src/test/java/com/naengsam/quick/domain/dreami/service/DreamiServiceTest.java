package com.naengsam.quick.domain.dreami.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 드리미 서비스 단위 테스트. 프로필 조회 시 이름/평점/거절횟수를 올바르게 조합하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DreamiServiceTest {

    @Mock
    private DreamiRepository dreamiRepository;

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;

    @InjectMocks
    private DreamiService dreamiService;

    private static BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    private static Boormi activeBoormi() {
        return Boormi.create("dreami@test.com", "pass123", "김드림", "01098765432",
                LocalDate.of(1995, 5, 5));
    }

    // ---------- getDreamiProfile ----------

    @Test
    void 프로필조회_정상이면_이름_평점_거절횟수를_담아_반환한다() {
        Boormi boormi = activeBoormi();
        UUID id = boormi.getBoormiId();
        Dreami dreami = Dreami.create(id, "idCardKey", "criminalRecordKey");
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));
        given(dreamiRequestDeniedDetailsRepository.countByDreamiId(id)).willReturn(2L);

        DreamiProfileDto result = dreamiService.getDreamiProfile(id);

        assertThat(result.name()).isEqualTo("김드림");
        assertThat(result.dreamiAvgScore()).isEqualByComparingTo(dreami.getDreamiAvgScore());
        assertThat(result.rejectCount()).isEqualTo(2L);
    }

    @Test
    void 프로필조회_드리미가_없으면_NOT_FOUND_예외() {
        UUID id = UUID.randomUUID();
        given(dreamiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> dreamiService.getDreamiProfile(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(DreamiErrorCode.NOT_FOUND);
    }

    @Test
    void 프로필조회_부르미가_없으면_NOT_FOUND_예외() {
        UUID id = UUID.randomUUID();
        Dreami dreami = Dreami.create(id, "idCardKey", "criminalRecordKey");
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(boormiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> dreamiService.getDreamiProfile(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(DreamiErrorCode.NOT_FOUND);
    }
}
