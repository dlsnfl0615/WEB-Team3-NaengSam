package com.naengsam.quick.domain.delivery.dto;

import java.util.UUID;

/**
 * 액션이 실행될 때 호출하는 매칭 상태 변경 로직.
 * dto → service 순환 의존을 피하기 위해 인터페이스를 dto 쪽에 두고 MatchingService가 구현한다.
 * 여기 선언된 apply* 메서드는 엔진 스레드에서만 호출되어야 한다(단일 스레드 직렬화).
 */
public interface MatchingContext {

    void applyRegisterDreami(UUID dreamiId, GeoPoint location);

    void applyRemoveDreami(UUID dreamiId);
}
