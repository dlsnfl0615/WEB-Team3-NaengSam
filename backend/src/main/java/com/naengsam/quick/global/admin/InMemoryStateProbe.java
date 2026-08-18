package com.naengsam.quick.global.admin;

import java.util.List;

/**
 * 힙에 상태를 들고 있는 빈이 자기 자료구조 현황을 보고하는 진단 전용 인터페이스. 이 서비스는 매칭·인증·SSE·레이트리밋 상당 부분이 DB가 아니라 JVM 힙 위의
 * {@code ConcurrentHashMap}/{@code Set}/{@code Queue}에서 돌아가는데, 그중 일부는 정리 경로가 없거나 특정 성공 경로에서만 동작한다. 지워졌어야 할 원소가 남아 있는지를 힙 덤프 없이
 * 확인하기 위한 것이며, {@link InMemoryStateController}가 모든 구현 빈을 주입받아 한 번에 스냅샷을 만든다.
 *
 * <p>보고 대상 자료구조는 전부 동시성 컬렉션이라 순회 자체는 안전하지만(weakly consistent), 순회 도중 다른 스레드가 값을 바꿀 수 있어 size는 근사치다. 진단 용도에는 충분하므로 잠금을
 * 걸지 않는다.
 */
public interface InMemoryStateProbe {

    /**
     * 이 빈이 들고 있는 인메모리 자료구조들의 현재 상태.
     */
    List<InMemoryStructureDto> inMemoryStructures();
}
