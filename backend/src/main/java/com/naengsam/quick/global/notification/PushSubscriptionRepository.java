package com.naengsam.quick.global.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    /** endpoint가 전역 unique이므로 소유자를 몰라도 대상을 특정할 수 있다(소유자 재배정의 근거). */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 한 사용자에게 푸시를 보낼 때의 팬아웃 대상. 모든 푸시마다 실행되므로 boormi_id 인덱스가 필요하다. */
    List<PushSubscription> findAllByBoormiId(UUID boormiId);
}
