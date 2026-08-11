# 1. 단일 DelayQueue 기반 MatchingScheduler 추가

`MatchingEngine`(즉시 실행, `LinkedBlockingQueue`)과 `MatchingActionScheduler`(지연/반복 실행, `DelayQueue`)를 하나로
합치기 위한 첫 단계로, `domain/matching/service/scheduler` 패키지에 `MatchingScheduler`를 새로 추가했다.

## 무엇을

- `MatchingScheduler`: 단일 `DelayQueue<ScheduledAction>` + 전용 워커 스레드(virtual thread) 하나로 즉시(`submit`)/지연
  (`schedule`)/반복(`scheduleRepeating`) Action을 모두 처리한다.
- `ScheduledAction`: 실행 시각(`executeAtNanos`)이 같으면 제출 순서(`sequence`)로 정렬해, `delay=0`인 즉시 제출도
  FIFO가 보장되도록 했다. `AtomicBoolean cancelled`를 반복 회차끼리 공유해서, 취소가 특정 회차가 아니라 예약 전체에
  적용되게 했다. 실행 시각은 `currentTimeMillis()`(NTP 보정으로 뒤로 튈 수 있음) 대신 단조 증가하는
  `System.nanoTime()` 기준으로 계산한다.
- `ScheduledActionHandle`: `schedule`/`scheduleRepeating`이 반환하는 취소 핸들.
- `Action`: 기존 `service.Action`은 구체 매칭 액션만 허용하는 sealed interface라 테스트 더블을 만들 수 없어서, 이
  패키지 전용의 단순한 `@FunctionalInterface Action`을 새로 뺐다. 기존 `service.Action`/`service.ScheduledAction`과는
  이름만 같고 서로 무관하다.

## 왜 새 패키지인가

기존 `MatchingEngine`/`MatchingActionScheduler`는 아직 운영 코드에 연결된 채로 그대로 둔다. 같은 패키지에
`ScheduledAction`을 또 만들 수 없어서, 연결 전까지는 독립된 `service.scheduler` 패키지에서 개발하고, 연결하는
커밋에서 기존 클래스들을 정리(삭제)하는 방식으로 진행한다.

## 테스트

`MatchingSchedulerTest`에서 즉시 실행, 제출 순서 FIFO, 지연 실행, 빠른 Action 우선 실행, 동일 실행 시각에서의
sequence 순서, Action 예외 후 워커 생존, 취소된 지연/반복 Action 미실행, 전체 Action이 동일 스레드에서 실행되는지를
검증한다.
