# 2. 즉시·지연 Action을 단일 Scheduler로 전환

`MatchingEngine`(즉시 실행)과 `MatchingActionScheduler`(지연/반복 실행)를 걷어내고, 매칭 도메인의 모든 Action이
`MatchingScheduler` 하나만 거치도록 실제 운영 코드를 연결했다.

## 무엇을

- `MatchingScheduler`에 `@Component`/`@PostConstruct`/`@PreDestroy`를 붙여 실제 빈으로 등록.
- 도메인 Action record 12개(`DreamiRegister`, `StartMatching`, `ExpireDreamiOffer` 등)가 기존 sealed
  `service.Action` 대신 `scheduler.Action`을 구현하도록 import만 교체 — 각 Action 객체를 그대로
  `matchingScheduler.submit`/`schedule`에 넘길 수 있어, 캡슐화를 깨는 람다 래핑 없이 기존 테스트의
  `instanceof` 검증이 그대로 유지된다.
- `MatchingService`: `matchingEngine`/`matchingActionScheduler` 필드를 `matchingScheduler` 하나로 합치고,
  모든 `submit()` 호출과 오퍼 timeout 예약(`scheduleDreamiOfferTimeout`/`scheduleBoormiOfferTimeout`)을
  `matchingScheduler` 기준으로 교체. 재매칭 fallback 스캔은 기존 Spring `@Scheduled(fixedRate=...)` 대신
  `@PostConstruct`에서 `matchingScheduler.scheduleRepeating(...)`으로 등록해, Spring의 별도 스케줄링 스레드가
  아니라 매칭 스케줄러의 단일 워커에서 반복 실행되게 했다. (기존처럼 즉시 한 번 스캔하는
  `scheduleRematchWaitingGroups()` public 메서드는 그대로 남겨 디버그 컨트롤러·테스트에서 계속 쓴다.)
- `MatchingBatchDispatcher`: 의존성을 `MatchingActionScheduler` → `MatchingScheduler`로 교체.
- `MatchingPlanApplier`: 의존성을 `MatchingActionScheduler` → `MatchingService`로 교체. `ExpireDreamiOffer`가
  `service` 패키지 전용(package-private) record라 다른 패키지에서 직접 만들 수 없어서, `MatchingService`에 새
  public 메서드 `scheduleDreamiOfferTimeout(offerId, ttl)`을 추가하고 `MatchingPlanApplier`는 그걸 호출한다.
  (순환 의존은 `MatchingPolicyConfiguration`에서 기존 패턴대로 `@Lazy MatchingService`로 끊는다.)
- `MatchingEngine`/`MatchingActionScheduler`/구 `service.Action`/구 `service.ScheduledAction` 삭제.

## 테스트

- `MatchingServiceTest`/`MatchingBatchDispatcherTest`/`MatchingPlanApplierTest`/`MatchingPolicyConfigurationTest`:
  mock 대상을 `matchingEngine`/`matchingActionScheduler` → `matchingScheduler`(또는 `matchingService`)로 교체.
- `MatchingServiceConcurrencyTest`: mock 대신 실제 `MatchingScheduler`를 띄워 검증하던 기존 구조를 그대로
  유지하면서 대상만 `MatchingEngine` → `MatchingScheduler`로 교체. "timeout과 사용자 응답이 거의 동시에
  들어오는" 테스트가 이제 진짜로 같은 워커 스레드 하나에서 즉시 액션과 timeout 액션을 처리하는지를 검증한다.
- `MatchingSchedulerIntegrationTest`(신규): 실제 `MatchingScheduler`+`MatchingBatchDispatcher`+
  `MatchingPlanApplier`를 조합해, 배치 window(짧게 50ms)가 지나 오퍼가 열리고 그보다 늦게 만료되도록 예약한
  오퍼 timeout(150ms)이 순서대로 실행되어 오퍼가 `DREAMI_EXPIRED`로 전이되는지 확인한다. 배치 Action과 오퍼
  timeout Action이 서로 다른 지연으로 같은 스케줄러에 올라가도 지연 순서대로 처리됨을 보여준다.
- `MatchingActionSchedulerTest` 삭제(대상 클래스 삭제로 커밋 1의 `MatchingSchedulerTest`가 대체).
