package com.naengsam.quick.global.debug;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 인메모리 자료구조 하나의 현재 상태. {@code size}만으로는 "왜 안 지워졌는지"를 알 수 없으므로, 상태별 집계({@code breakdown})와 남아 있는 키 샘플({@code
 * samples})을 함께 담는다.
 *
 * <p>{@code samples}는 키가 민감정보가 아닐 때만 채운다. 휴대폰번호(인증코드·SMS 레이트리밋), 세션 ID처럼 그대로 노출하면 안 되는 키는 {@link #ofSize}로 크기만 보고한다.
 * 값(비밀번호·인증코드 등)은 어떤 경우에도 담지 않는다.
 */
@Schema(description = "인메모리 자료구조 하나의 현재 상태")
public record InMemoryStructureDto(
        @Schema(description = "필드명", example = "offersById")
        String name,

        @Schema(description = "키/값이 의미하는 것", example = "offerId → 오퍼")
        String description,

        @Schema(description = "원소 개수", example = "127")
        int size,

        @Schema(description = "상태별 집계 등 세부 내역. 없으면 빈 맵")
        Map<String, Long> breakdown,

        @Schema(description = "남아 있는 키 샘플(최대 5개). 키가 민감정보면 빈 목록")
        List<String> samples
) {

    private static final int SAMPLE_LIMIT = 5;

    /**
     * 맵의 크기와 키 샘플을 보고한다. 키가 민감정보가 아닐 때만 쓴다.
     */
    public static InMemoryStructureDto ofMap(String name, String description, Map<?, ?> map) {
        return new InMemoryStructureDto(name, description, map.size(), Map.of(), sample(map.keySet()));
    }

    /**
     * 컬렉션의 크기와 원소 샘플을 보고한다.
     */
    public static InMemoryStructureDto ofCollection(String name, String description, Collection<?> values) {
        return new InMemoryStructureDto(name, description, values.size(), Map.of(), sample(values));
    }

    /**
     * 크기만 보고한다. 키가 휴대폰번호·세션 ID처럼 노출하면 안 되는 자료구조에 쓴다.
     */
    public static InMemoryStructureDto ofSize(String name, String description, int size) {
        return new InMemoryStructureDto(name, description, size, Map.of(), List.of());
    }

    /**
     * 상태별 집계를 덧붙인 새 인스턴스를 만든다. 갱신할 때마다 화면의 항목 순서가 흔들리지 않도록 넘겨받은 순서를 그대로 보존한다({@link Map#copyOf}는 순회 순서를 보장하지 않는다).
     */
    public InMemoryStructureDto withBreakdown(Map<String, Long> breakdown) {
        return new InMemoryStructureDto(name, description, size,
                Collections.unmodifiableMap(new LinkedHashMap<>(breakdown)), samples);
    }

    private static List<String> sample(Collection<?> values) {
        return values.stream()
                .limit(SAMPLE_LIMIT)
                .map(String::valueOf)
                .toList();
    }
}
