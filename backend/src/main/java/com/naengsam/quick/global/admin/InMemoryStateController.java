package com.naengsam.quick.global.admin;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.session.AdminUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 힙에 남아 있는 인메모리 자료구조 현황을 한 번에 조회하는 디버그 전용 API. 매칭·인증·SSE 상태가 DB가 아니라 JVM 힙에 있어 정리되지 않은 원소가 쌓여도 눈에 띄지 않는데, 이 스냅샷을 주기적으로
 * 비교하면 "늘기만 하고 줄지 않는" 자료구조를 힙 덤프 없이 찾을 수 있다.
 *
 * <p>조회 전용이다. 진행 중인 매칭·배달·세션을 망가뜨릴 수 있어 강제 정리 기능은 두지 않는다. 관리자 계정으로 로그인해야 호출할 수 있다({@link AdminUser}).
 */
@Tag(name = "[Admin] In-Memory State", description = "힙에 남아있는 인메모리 자료구조 현황을 조회하는 관리자 전용 API")
@RestController
@RequestMapping("/api/v1/admin/inmemory")
@RequiredArgsConstructor
public class InMemoryStateController {

    private final List<InMemoryStateProbe> probes;
    private final Clock clock;

    @Operation(summary = "인메모리 자료구조 현황 스냅샷",
            description = "상태를 들고 있는 빈들을 순회하며 각 자료구조의 원소 수, 상태별 집계, 남아 있는 키 샘플을 반환한다. "
                    + "두 번 호출해 크기를 비교하면 정리되지 않는 자료구조를 찾을 수 있다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED", "FORBIDDEN_ROLE"})
    @GetMapping
    public InMemoryStateDto snapshot(@AdminUser UUID adminId) {
        List<OwnerView> owners = probes.stream()
                .map(OwnerView::from)
                // 매 갱신마다 순서가 흔들리면 화면이 튀므로 소유 클래스 이름순으로 고정한다.
                .sorted(Comparator.comparing(OwnerView::owner))
                .toList();
        int totalElements = owners.stream()
                .flatMap(owner -> owner.structures().stream())
                .mapToInt(InMemoryStructureDto::size)
                .sum();
        int totalStructures = owners.stream()
                .mapToInt(owner -> owner.structures().size())
                .sum();

        return new InMemoryStateDto(LocalDateTime.now(clock), totalElements, totalStructures, owners);
    }

    @Schema(description = "인메모리 자료구조 현황 스냅샷")
    record InMemoryStateDto(
            @Schema(description = "스냅샷을 뜬 시각") LocalDateTime capturedAt,
            @Schema(description = "모든 자료구조의 원소 수 합계") int totalElements,
            @Schema(description = "보고된 자료구조 개수") int totalStructures,
            @Schema(description = "상태를 들고 있는 빈별 내역") List<OwnerView> owners) {
    }

    @Schema(description = "상태를 들고 있는 빈 하나의 자료구조 목록")
    record OwnerView(
            @Schema(description = "빈의 클래스명", example = "MatchingService") String owner,
            List<InMemoryStructureDto> structures) {

        static OwnerView from(InMemoryStateProbe probe) {
            // @Transactional 빈은 CGLIB 프록시라 getClass()를 그대로 쓰면 MatchingService$$SpringCGLIB$$0 이 나온다.
            return new OwnerView(ClassUtils.getUserClass(probe).getSimpleName(), probe.inMemoryStructures());
        }
    }
}
