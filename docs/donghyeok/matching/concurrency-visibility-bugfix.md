# 동시성 테스트 실패 원인과 수정: "단일 기록자"만으로는 부족했다

`com.naengsam.quick.domain.matching`

[`matching-domain-design-philosophy.md`](./matching-domain-design-philosophy.md)는 "모든 mutation이 엔진 스레드 하나에서만 일어난다는 불변식이 지켜지는 한, 가변 상태 + 락 없음 조합은 안전하다"고 적고 있다. `MatchingServiceConcurrencyTest`가 실제로 실패하면서 드러난 것은, 이 명제가 **쓰기 순서**에 대해서는 맞지만 **읽기 가시성**에 대해서는 충분하지 않았다는 점이다. 이 문서는 `./gradlew test`가 간헐적으로 실패하던 원인을 어떻게 좁혀갔는지와, 그래서 무엇을 고쳤는지를 기록한다.

## 증상: 혼자 돌리면 통과, 같이 돌리면 실패

`MatchingServiceConcurrencyTest`의 5개 테스트 중 4개가 실패했다. 그런데 실패 양상이 두 가지로 뚜렷하게 갈렸다.

1. `동시에_같은_제안을_여러번_수락해도...`, `동시에_같은_주문에_매칭을_시작해도...` — 항상 실패(결정적).
2. `수락이_취소보다_먼저...`, `취소가_수락보다_먼저...` — 단독 실행 시 통과, 클래스 전체를 같이 돌리면 실패(비결정적, flaky).

"단독으론 되는데 같이 돌리면 깨진다"는 전형적인 JMM(Java Memory Model) 가시성 문제의 징후라서, 로그를 붙여 순서를 직접 확인하는 방식으로 원인을 좁혔다.

## 원인 1: 테스트 자체의 데드락 (결정적 실패)

```java
private ExecutorService requestThreads; // newFixedThreadPool(16)
...
int concurrentAccepts = 50; // 또는 concurrentStarts = 30
CountDownLatch ready = new CountDownLatch(concurrentAccepts);
CountDownLatch go = new CountDownLatch(1);
for (int i = 0; i < concurrentAccepts; i++) {
    requestThreads.submit(() -> {
        ready.countDown();
        awaitLatch(go); // go가 열릴 때까지 블록
        matchingService.acceptByDreami(offerId);
    });
}
ready.await(5, TimeUnit.SECONDS); // 50개가 모두 countDown 해야 풀림
go.countDown();
```

스레드 풀 크기(16)보다 동시 작업 수(50, 30)가 많다. 먼저 스케줄된 16개가 `ready.countDown()` 후 `go`를 기다리며 블록되면, 나머지 34개는 풀에 자리가 나지 않아 시작조차 못 하고, `ready`는 절대 0에 도달하지 못한다 — 테스트가 스스로를 데드락시키는 구조였다. 이건 프로덕션 코드 문제가 아니라 테스트의 "ready/go 장벽" 패턴과 스레드 풀 크기가 어긋난 것이므로, 풀 크기를 건드리는 대신 `concurrentAccepts`/`concurrentStarts`를 풀 크기(16) 이하로 낮춰 테스트 규모를 줄였다.

## 원인 2: 크로스스레드 가시성 부재 (비결정적 실패)

`수락이_취소보다_먼저...`류 테스트를 단독/반복 실행하며 `MatchingEngine`과 `MatchingService`에 임시 디버그 로그를 심어 실제 실행 순서를 찍어봤다. 엔진 스레드는 액션을 정확한 FIFO 순서로, 완전히 직렬로 처리하고 있었다 — `AcceptByDreami` 다음에 `CancelOrderByBoormi`가 로그상으로도 순서대로 실행됐다. **로직 자체는 처음부터 맞았다.**

문제는 자료구조 쪽이었다.

- `MatchingService`의 `dreamiMap`, `offersById`, `orderOfferGroupsByOrderId` 등은 평범한 `HashMap`이었다. 쓰기는 엔진 스레드 하나뿐이지만, `findOrderOfferGroup` 같은 조회 메서드는 호출한 스레드(테스트/컨트롤러 스레드)에서 아무 동기화 없이 그 맵을 직접 읽는다. `HashMap`은 "한 스레드만 쓰고 다른 스레드는 읽기만 해도" 안전하지 않다 — 내부 리사이즈 등과 읽기가 겹치면 정의되지 않은 동작이 날 수 있다.
- `MatchOffer.status`, `WaitingDreami.status`/`updatedAt`, `OrderOfferGroup.status`/`rematchRequired` 필드가 `volatile`이 아닌 평범한 필드였다. 엔진 스레드의 쓰기가 다른 스레드의 읽기에 **즉시 보인다는 보장이 JMM 차원에서 없었다** — 실제로는 시스템/JIT 상태에 따라 우연히 보이거나 안 보이거나 했을 뿐이다.
- `OrderOfferGroup.offers`가 평범한 `ArrayList`였다. 엔진 스레드가 라운드마다 `addAll`로 append하는 동안 다른 스레드가 `offers()`(=`List.copyOf`)로 순회/복사하면 경합이 생긴다.

### 더 미묘한 두 번째 문제: 필드 하나만 기다리는 `awaitUntil`

가시성 문제를 고치고도 `취소가_수락보다_먼저...`가 여전히 드물게 실패했다. 디버그 로그로 실제 순간을 잡아보니, `applyCancelOrderByBoormi`는 (1) `offer.withdraw()` → (2) `group.cancel()` 순서로 **같은 액션 안에서 순차적으로** 두 개의 서로 다른 volatile 필드를 갱신한다. 테스트는 `awaitUntil`로 오직 오퍼 상태(1번)만 기다린 뒤 곧바로 그룹 상태(2번)를 단언했다 — 그런데 1번과 2번 사이에는 미세하지만 실존하는 시간차가 있다. 운 나쁘게 테스트 스레드의 폴링이 정확히 그 틈을 잡으면 "오퍼는 이미 WITHDRAWN인데 그룹은 아직 OPEN"인 찰나를 관찰하게 된다. `volatile`은 각 필드의 최신값을 보장할 뿐, 여러 필드에 걸친 하나의 액션 전체를 원자적 스냅샷으로 보이게 해주지는 않는다.

즉 이 테스트가 진짜로 기다려야 했던 조건은 "오퍼가 WITHDRAWN"이 아니라 "이 액션이 최종적으로 만드는 상태(오퍼 WITHDRAWN **그리고** 그룹 CLOSED)"였다. `awaitUntil` 조건에 그룹 상태까지 포함시켜 테스트가 실제로 단언하려는 최종 상태를 기다리도록 고쳤다.

## 수정 내용

**프로덕션 코드** (`MatchingService.java`)
- `dreamiMap`, `offersById`, `offerIdsByDreamiId`, `orderOfferGroupsByOrderId`: `HashMap` → `ConcurrentHashMap`.
- `MatchOffer.status`, `WaitingDreami.status`/`updatedAt`, `OrderOfferGroup.status`/`rematchRequired`: `volatile` 추가.
- `OrderOfferGroup.offers`: `ArrayList` → `CopyOnWriteArrayList`.

읽기 빈도 대비 쓰기(append)가 드물고, 쓰기는 여전히 엔진 스레드 하나뿐이므로 `CopyOnWriteArrayList`/`ConcurrentHashMap`의 오버헤드는 무시할 만하다. "단일 기록자"라는 기존 설계는 그대로 유지하면서, 그 결과를 **다른 스레드에서 안전하게 읽을 수 있도록** 최소한의 동시성 자료구조로 보강한 것이다.

**테스트 코드** (`MatchingServiceConcurrencyTest.java`)
- `concurrentAccepts`, `concurrentStarts`를 각각 50/30 → 16(스레드 풀 크기)으로 축소해 자기 자신을 데드락시키던 구조를 제거.
- `수락이_취소보다_먼저...`, `취소가_수락보다_먼저...`의 `awaitUntil` 조건에 그룹 상태(CLOSED)까지 포함시켜, 액션 하나가 여러 필드를 순차적으로 갱신하는 동안의 중간 상태를 관찰하지 않도록 수정.

## 남은 트레이드오프

`ConcurrentHashMap`/`volatile`/`CopyOnWriteArrayList`는 "여러 필드에 걸친 하나의 트랜잭션을 원자적으로 보이게" 해주지는 않는다. 지금 고친 두 테스트처럼, 한 액션이 여러 필드를 순차 갱신하는 경우 그 필드들을 개별적으로 기다리는 테스트/호출부가 있다면 같은 종류의 미세한 틈이 또 나타날 수 있다. 근본적으로 없애려면 액션 완료를 나타내는 단일 신호(예: 액션별 완료 콜백/Future, 혹은 조회 자체를 엔진 스레드에 위임)가 필요하지만, 지금 규모에서는 "실제로 관찰되는 최종 상태까지 기다린다"는 테스트 관례로 충분하다고 판단했다.
