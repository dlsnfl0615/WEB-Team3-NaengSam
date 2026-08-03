# Matching 도메인 설계 철학

`com.naengsam.quick.domain.matching`

## 왜 엔티티도, 리포지토리도, 테이블도 없는가

Matching 도메인은 JPA 엔티티가 없다. `HashMap` 기반의 인메모리 상태만 존재한다. 이는 실수나 미완성이 아니라 의도된 설계다.

매칭은 "지금 이 순간 온라인인 드리미에게 실시간으로 제안을 뿌리고, 누가 먼저 수락하는지 겨루는" 라이브 경매(live auction)에 가깝다. 주문(Order)처럼 영속되어야 할 결과가 아니라, 결과에 도달하기 전까지 초 단위로 요동치는 휘발성 상태다. 서버가 재시작되면 진행 중이던 제안들은 사라지지만, 이는 감수 가능한 트레이드오프로 간주되었다 — 아직 프로덕션 이전 단계이고(`MatchingDebugController`가 `@PublicApi`로 인증 없이 열려 있으며 프로덕션 전 제거가 필요하다고 명시된 것이 그 증거), 매칭 실패 시 재시도(`rematchRequired`) 로직이 이미 "복구 가능한 일시적 상태"를 전제로 설계되어 있기 때문이다.

Git 히스토리에서도 이 지향점이 드러난다.
- `BE/Refactor : Matching 로직 도메인 분리` — 매칭을 별도 도메인으로 분리
- `Revert "...Matching을 Delivery 도메인에서 분리..."` — Delivery로의 병합 시도가 되돌려짐
- `BE/Fix : 일시적으로 비동기화되던 action들을 매칭 엔진에 넣어서 동기화` — 흩어져 있던 액션들을 하나의 엔진으로 모아 동기화

즉 팀은 "매칭은 영속 애그리거트가 아니라 회전율 높은 휘발성 상태"라는 결론에 점진적으로 수렴했고, DB 트랜잭션/락 대신 단일 엔진 뒤로 상태를 완전히 감추는 방향을 택했다.

## 액터 모델: 왜 synchronized나 락이 아닌 단일 스레드 큐인가

`MatchingEngine`은 `LinkedBlockingQueue<Action>`과 이를 소비하는 **가상 스레드(virtual thread) 하나**로 구성된다. `MatchingService`의 모든 public 메서드(`acceptByDreami`, `registerDreami` 등)는 인자를 `Action` 레코드로 감싸 큐에 `submit`할 뿐, 상태를 직접 건드리지 않는다. 실제 변경은 엔진 스레드에서만 호출되는 package-private `apply*` 메서드 안에서 일어난다.

```
컨트롤러 스레드 → Action 생성 → 큐에 enqueue → (단일 가상 스레드) → apply*() 실행 → SSE 이벤트 발행
```

이 구조의 목적은 엔진 javadoc에 명시되어 있다: "모든 매칭 상태 변경을 이 스레드 하나로 직렬화하는 것이 목적이다." 동시에 들어오는 여러 HTTP 요청이 있어도, 상태 변경은 항상 하나의 스레드에서 순서대로 일어나므로 `HashMap`에 별도의 동기화 장치(락, `ConcurrentHashMap` 등)가 필요 없다. 가상 스레드를 쓰는 이유는 "큐 소비자 전용 스레드 하나를 통째로 점유"하는 비용이 OS 스레드 대비 거의 없기 때문이다.

같은 패턴이 `SseService`에도 재사용된다 — 순서 보장과 느리거나 죽은 클라이언트 연결로부터의 격리를 위해 역시 전용 단일 가상 스레드를 쓴다. 이는 우연이 아니라 "한 관심사당 액터 스레드 하나"라는 팀의 의식적인 아키텍처 패턴이다.

이 구조가 만드는 트레이드오프도 명확하다. `submit`은 `LinkedBlockingQueue.offer`의 실패 가능성 때문에 `boolean`을 반환하고, 컨트롤러가 즉시 `409 CONFLICT`를 응답해야 하는 경우(`startMatching`)에는 enqueue 전에 한 번, 큐 안에서 처리될 때 또 한 번 — 총 두 번 중복 체크를 한다. 비동기 큐이면서도 클라이언트에게는 동기적인 응답을 줘야 하는 데서 오는 불가피한 이중 검증이다.

## 가변 클래스를 쓰는 이유: record와의 의도적 결별

`MatchOffer`, `WaitingDreami`, `OrderOfferGroup`은 이 코드베이스의 다른 곳(DTO 등)과 달리 `record`가 아니라 가변 클래스다. 이유는 명시적으로 주석에 남아 있다: "status가 계속 바뀌므로 record가 아닌 가변 클래스로 변경." record였다면 상태 전이마다 `withStatus()` + `HashMap` 재삽입이 필요했을 것이다.

이 선택이 안전한 이유는 오직 하나 — 위에서 설명한 단일 기록자(single-writer) 스레드 보장 때문이다. 가변 상태 + 락 없음이라는 조합은 일반적으로 위험하지만, 모든 mutation이 한 스레드에서만 일어난다는 불변식이 지켜지는 한 안전하다. 즉 "record 대신 가변 클래스"라는 선택은 액터 모델이라는 더 큰 설계와 짝을 이루는 하위 결정이다.

그 대신 상태 전이 자체는 `requireStatus(...)` 가드로 엄격하게 지킨다. 영속화도 낙관적 락도 없는 객체이기 때문에, 잘못된 상태 전이가 조용히 버그로 남는 것을 막기 위해 `IllegalStateException`을 즉시 던지는 방식으로 방어한다.

## 매칭 라운드 알고리즘

`attemptOfferRound`는 다음 순서로 동작한다.

1. 해당 주문 그룹에서 이미 제안을 받았던(거절/만료/철회) 드리미 ID를 제외한다 — 같은 주문으로 재알림 스팸을 보내지 않기 위해.
2. `WaitingDreamiStatus.MATCHING` 상태(현재 대기 중)인 드리미만 후보로 남긴다.
3. `updatedAt` 기준 오래 대기한 순으로 정렬한다. 코드에는 `// TODO: 거리순 등 실제 정렬 기준 확정 전까지는 대기 오래한 순`이라는 주석이 있다 — 즉 거리 기반 스코어링이 최종 설계이고, 현재의 FIFO는 확정 전 임시 공정성 대리 지표다.
4. 최대 3명(`MAX_OFFER_COUNT`)에게 동시에 제안을 보낸다 — 선착순 경쟁 구조.
5. 각 제안에는 30초(`OFFER_TTL`, 역시 "정책 확정 후 조정" TODO) 만료 시각이 붙는다.
6. 후보가 없으면 에러 대신 `rematchRequired = true`로 그룹을 닫아, 나중에 자동 재시도되게 한다.

**선착순 수락 처리(`applyAcceptByDreami`)**: 한 드리미가 수락하면 해당 제안만 `PENDING_BOORMI_CONFIRMATION`으로 전이하고, 같은 그룹에서 아직 `OFFERED` 상태인 나머지 제안들은 전부 철회(`withdraw`)되며 그 드리미들은 다시 `MATCHING`으로 풀에 복귀한다. 이미 거절/만료된 제안은 건드리지 않는다 — "아직 살아있는 경쟁자만 정리한다"는 원칙이 명시적으로 지켜진다.

## `rematchRequired`와 `cancel()`의 구분: 일시적 실패 vs 종결

`OrderOfferGroup`이 `CLOSED`되는 경로는 두 가지 의미를 갖는다.
- **재시도 가능**(`rematchRequired = true`): 드리미가 없어서, 혹은 확정된 후보가 거절/만료돼서 닫힌 경우. `registerDreami`로 새 드리미가 들어올 때마다 `retryRematchWaitingGroups`가 이런 그룹들을 스캔해 되살린다.
- **종결**(`cancel()`, `rematchRequired = false`): 부미가 직접 주문을 취소한 경우. 이 경우엔 자동 재시도 대상에서 명시적으로 제외된다.

같은 `CLOSED` 상태값 안에 "일시적으로 매칭 상대가 없다"와 "이 주문 자체가 끝났다"라는 서로 다른 비즈니스 의미를 플래그 하나로 구분해 담은 것은, 상태 enum만으로는 표현할 수 없는 의도를 최소한의 필드로 인코딩한 설계다.

## 도메인 경계: Matching과 Dreami, Matching과 Order

- `WaitingDreamiStatus`(`MATCHING`/`PROPOSED`, 매칭 도메인 소유·휘발성)와 `DreamiCd`(`REQUESTED/REVIEWING/APPROVED/REJECTED`, 드리미 도메인 소유·영속)는 의도적으로 분리되어 있다. 하나는 "승인된 드리미가 지금 당장 온라인이고 이용 가능한가", 다른 하나는 "이 사용자의 드리미 자격 심사가 통과됐는가"로, 생명주기 자체가 다르기 때문에 하나로 합치지 않는다.
- `Orders`의 `OrderCd`(`MATCHING, PENDING_BOORMI_CONFIRMATION, IN_PROGRESS, WAITING_CONFIRMATION, COMPLETED, CANCELLED, CLAIM_REVIEW`)는 인메모리 `MatchOfferStatus`/`OrderOfferGroupStatus`보다 훨씬 세분화되어 있다. Matching 도메인은 "경매(auction)" 하위 단계만 모델링하고, 배송 진행·정산·클레임 같은 이후 상태는 Order/Delivery 도메인의 책임으로 남긴다 — Delivery로의 병합 시도가 한 번 revert된 이력과 맞물려, 좁은 도메인 경계를 지키려는 의식적 선택으로 읽힌다.
- `Orders.create(...)`에는 "Matching Service의 기존 객체 compatibility를 위한 임시 생성자"라는 주석이 있다. Matching이 먼저 만들어지고 Address/Order 도메인이 나중에 다듬어지면서 생긴 과도기적 흔적이다.

## 알려진 미완성 지점 (설계 철학상의 트레이드오프)

- 거리 기반 실제 스코어링은 미구현 — 현재는 대기 시간순 FIFO.
- 30초 offer TTL은 정책 확정 전 임시값.
- `proceedToDelivery`가 빈 스텁 — 매칭 확정 이후 배송 도메인으로의 연결 고리가 아직 없다.
- 매칭 상태는 전혀 영속화되지 않는다 — 서버 재시작 시 진행 중인 제안/대기 드리미 정보가 모두 소실된다. "휘발성 라이브 경매"라는 설계 자체의 자연스러운 귀결이지만, 프로덕션화 전에 재점검이 필요한 지점으로 남겨둔다.
