# 재매칭(Rematch) 스케줄러 설계 철학

`com.naengsam.quick.domain.matching`

이 문서는 [`matching-domain-design-philosophy.md`](./matching-domain-design-philosophy.md)에서 설명한 액터 모델을 전제로,
`MatchingService.scheduleRematchWaitingGroups()`가 왜 지금과 같은 모양을 하고 있는지를 다룬다.

## 왜 스케줄러가 상태를 직접 건드리지 않는가

`@Scheduled` 메서드는 Spring이 관리하는 별도의 `TaskScheduler` 스레드 풀에서 실행된다. 반면 `MatchingEngine`의 존재 이유는 "모든 매칭 상태 변경을 단일 가상 스레드로
직렬화"하는 것이다(엔진 javadoc 참고). 스케줄러 스레드가 `dreamiMap`이나 `orderOfferGroupsByOrderId`를 직접 스캔·수정한다면, 컨트롤러에서 들어온 다른 액션(수락, 거절, 만료
등)과 경합해 이 코드베이스가 지금까지 지켜온 "가변 상태 + 락 없음, 대신 단일 기록자"라는 불변식이 깨진다.

그래서 `scheduleRematchWaitingGroups()`는 다른 모든 `public` 진입점과 동일한 패턴을 따른다 — 직접 실행하지 않고 `RematchWaitingGroups` 액션을 만들어 큐에
`submit`할 뿐이다. 실제 재매칭 판정은 여전히 엔진 스레드 안, `applyRematchWaitingGroups()` → `retryRematchWaitingGroups()` 경로에서만 일어난다.

```
스케줄러 스레드 → RematchWaitingGroups 생성 → 큐에 enqueue → (단일 가상 스레드) → applyRematchWaitingGroups() → attemptOfferRound()
```

즉 트리거의 출처(HTTP 요청이든, 스케줄러든)는 여러 개일 수 있지만, 상태를 바꾸는 통로는 항상 하나다.

## 왜 기존 로직을 새로 만들지 않고 감싸기만 했는가

`retryRematchWaitingGroups()`는 이미 드리미 등록 시점(`applyRegisterDreami`)에 재사용되고 있었다. 시간 기반 재매칭과 드리미 등록 기반 재매칭은 "CLOSED +
rematchRequired 상태의 방을 다시 오퍼 라운드에 태운다"는 동일한 판정 로직에 대한 서로 다른 트리거일 뿐이다. 트리거가 하나 늘었다고 판정 로직까지 복제하면, 두 트리거의 조건이 시간이 지나며 조용히
벌어지는 위험이 생긴다. 그래서 새 액션(`RematchWaitingGroups`)은 얇은 래퍼(`applyRematchWaitingGroups`)를 거쳐 기존 private 메서드를 그대로 호출하도록 만들었다.

## 왜 10분 간격이고, 왜 임시값인가

드리미가 새로 등록될 때마다 이미 재매칭이 시도되므로, 스케줄러는 "정상 경로"가 아니라 안전망(safety net)이다 — 드리미 등록이 뜸한 시간대에 재매칭 대기 방이 무기한 방치되는 사각지대를 메우기 위한
것이다. 그런 성격상 정확한 주기가 중요한 요구사항은 아니며, `MAX_OFFER_COUNT`나 `OFFER_TTL`처럼 이 도메인의 다른 매직 넘버들과 마찬가지로 정책이 확정되기 전의 잠정값이다(
`REMATCH_SCAN_INTERVAL`, `MatchingService.java`).

`@Scheduled`의 `fixedRate`/`fixedDelay` 속성은 상수 표현식만 허용하므로 `Duration` 필드를 애노테이션에 직접 넣을 수 없다. `REMATCH_SCAN_INTERVAL` 필드는
그래서 실행에 관여하지 않는 문서화 목적의 상수이고, 실제 값은 애노테이션에 리터럴(`600_000L`, 10분)로 중복 기재된다. `@Scheduled`에 밀리초 리터럴을 직접 넣는 패턴 자체는
`SmsSendRateLimiter.sweepExpired()`(`@Scheduled(fixedDelay = 3_600_000L)`, 1시간 주기)에도 이미 쓰이고 있다 — 다만 그쪽은 주기가 다르므로 "같은 주기를
관례로 따랐다"는 근거로 삼을 수는 없고, "리터럴 중복 기재 패턴이 기존에도 있었다"는 근거로만 참고한다.
