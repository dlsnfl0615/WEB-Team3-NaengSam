package com.naengsam.quick.global.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.global.admin.InMemoryStateController.InMemoryStateDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 인메모리 현황 스냅샷 조립 단위 테스트. 프로브들이 보고한 내용을 합계 내고 소유 클래스 이름순으로 고정하는 부분을 검증한다.
 */
class InMemoryStateControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    /** 프로브 구현체의 클래스명이 곧 화면의 소유자 이름이 되므로, 이름이 다른 두 개를 만든다. */
    private static final class ZebraStore implements InMemoryStateProbe {

        @Override
        public List<InMemoryStructureDto> inMemoryStructures() {
            return List.of(InMemoryStructureDto.ofSize("late", "나중 이름", 3));
        }
    }

    private static final class AlphaStore implements InMemoryStateProbe {

        @Override
        public List<InMemoryStructureDto> inMemoryStructures() {
            return List.of(
                    InMemoryStructureDto.ofMap("byId", "id → 값", Map.of(UUID.randomUUID(), "a")),
                    InMemoryStructureDto.ofCollection("queue", "대기열", List.of(1, 2, 3, 4)));
        }
    }

    private InMemoryStateController controller(InMemoryStateProbe... probes) {
        return new InMemoryStateController(List.of(probes), Clock.fixed(NOW, ZONE));
    }

    @Test
    void 스냅샷은_모든_프로브의_원소와_자료구조_개수를_합산한다() {
        InMemoryStateDto snapshot = controller(new ZebraStore(), new AlphaStore()).snapshot(UUID.randomUUID());

        assertThat(snapshot.totalElements()).isEqualTo(8);
        assertThat(snapshot.totalStructures()).isEqualTo(3);
        assertThat(snapshot.capturedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZONE));
    }

    @Test
    void 스냅샷의_소유_클래스는_이름순으로_고정된다() {
        InMemoryStateDto snapshot = controller(new ZebraStore(), new AlphaStore()).snapshot(UUID.randomUUID());

        assertThat(snapshot.owners()).extracting(InMemoryStateController.OwnerView::owner)
                .containsExactly("AlphaStore", "ZebraStore");
    }

    @Test
    void 상태를_보고하는_빈이_없으면_빈_스냅샷을_반환한다() {
        InMemoryStateDto snapshot = controller().snapshot(UUID.randomUUID());

        assertThat(snapshot.owners()).isEmpty();
        assertThat(snapshot.totalElements()).isZero();
        assertThat(snapshot.totalStructures()).isZero();
    }

    @Test
    void 자료구조_샘플은_최대_다섯개까지만_담는다() {
        List<Integer> tenValues = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        InMemoryStructureDto structure = InMemoryStructureDto.ofCollection("values", "값 목록", tenValues);

        assertThat(structure.size()).isEqualTo(10);
        assertThat(structure.samples()).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void 크기만_보고하는_자료구조는_샘플을_담지_않는다() {
        InMemoryStructureDto structure = InMemoryStructureDto.ofSize("codes", "민감한 키", 7);

        assertThat(structure.size()).isEqualTo(7);
        assertThat(structure.samples()).isEmpty();
        assertThat(structure.breakdown()).isEmpty();
    }

    @Test
    void 집계는_넘겨받은_순서를_그대로_보존한다() {
        Map<String, Long> ordered = new java.util.LinkedHashMap<>();
        ordered.put("OFFERED", 2L);
        ordered.put("MATCHED", 0L);
        ordered.put("offers 합계", 5L);

        InMemoryStructureDto structure = InMemoryStructureDto.ofSize("groups", "방", 1).withBreakdown(ordered);

        assertThat(structure.breakdown().keySet()).containsExactly("OFFERED", "MATCHED", "offers 합계");
    }
}
