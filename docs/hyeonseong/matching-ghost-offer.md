# 매칭 유령 오퍼 필터

응답할 수 없는 드리미에게 오퍼 슬롯이 나가던 문제와, SSE 연결 생존 여부로 후보를 거르기로 한 결정 기록.

## 문제

주문 하나에 동시에 나갈 수 있는 오퍼는 **최대 3개**(`MAX_OFFER_COUNT` / `matching.max-concurrent-offers=3`)이고, 드리미 응답 제한은 **30초**(`OFFER_TTL`)다. 이 3개는 희소 자원이다.

그런데 후보 선정에 **연결 생존 확인이 없었다.** 브라우저가 죽은 드리미에게 슬롯이 나가면 그 슬롯은 30초 동안 아무 일도 하지 않고 묶인다.

## 왜 유령이 생기는가

매칭 엔진의 대기 드리미 목록 `dreamiMap`(인메모리 `ConcurrentHashMap`)에서 **나가는 경로가 세 개뿐이다.**

| 경로 | 트리거 |
| --- | --- |
| `POST /api/v1/dreami/status/offline` | 드리미가 오프라인 버튼을 **직접** 누름 |
| 매칭 확정 | `dreamiMap.remove(matchOffer.dreamiId())` |
| 디버그 API | `MatchingDebugController` |

**SSE 연결이 끊겼을 때 매칭 상태를 정리하는 훅은 없다.** `SseEmitterRegistry`는 `MatchingService`를 모르고, 그 반대도 마찬가지다(의도된 분리다).

모바일 웹에서는 오프라인 버튼을 누르지 않고 사라지는 쪽이 오히려 흔하다 — 앱 스와이프 종료, 탭 메모리 회수, 브라우저 강제 종료, 기기 전원 차단. 이 드리미들은 `dreamiMap`에 `MATCHING` 상태로 **영구히 남는다.**

## 증거

`sse.events.dropped{reason=not_connected}` 카운터가 이미 이 일이 실제로 벌어지고 있음을 보여주고 있었다. 미연결 사용자에게 이벤트를 보내려 할 때마다 증가한다.

```java
// SseEmitterRegistry.java
public void send(UUID userId, String eventName, Object payload) {
    Map<String, SseEmitter> connections = emitters.get(userId);
    if (connections == null || connections.isEmpty()) {
        meterRegistry.counter("sse.events.dropped", "reason", "not_connected").increment();
        log.debug("미연결 사용자에게 전송 시도, 무시: userId={}, event={}", userId, eventName);
        return;   // 예외 없음
    }
    ...
}
```

## 실제로 무슨 일이 일어났나

핵심은 **"보내려다 실패"가 아니라 "오퍼가 정상 생성되고 슬롯이 소비됐다"**는 것이다.

`attemptOfferRound`는 후보 3명 각각에 대해 아래를 **연결 여부와 무관하게** 전부 실행한다.

1. `MatchOffer` 생성 → `offersById` / `offerIdsByDreamiId`에 등록
2. `dreami.markProposed()` → 드리미 상태가 `MATCHING` → `PROPOSED`
3. `scheduleDreamiOfferTimeout(offerId, 30s)` 예약
4. `group.addOffersAndOpen(newOffers)` → 방이 `OPEN`

마지막 SSE 전송 단계에서만 조용히 떨어진다. 예외가 없으므로 매칭 상태 기계는 **"3명에게 오퍼가 나갔다"**로 확정한 뒤이고, 실제로 받은 사람은 1명이다.

### 30초 뒤

유령의 오퍼는 `ExpireDreamiOffer`로 만료되어 `DREAMI_EXPIRED`가 된다. 그 뒤 두 가지가 따라붙는다.

- `MatchOffer.shouldExcludeFromRematch()`가 `DREAMI_EXPIRED → true` → 이 방의 재제안 후보에서 제외
- `OutcomeCooldownOfferPolicy`가 `matching.cooldown.dreami-expiration=10m` 적용

쿨다운은 전역 페널티가 아니라 **(주문, 드리미) 쌍 단위**다. `MatchingAssignmentProblemAssembler.findPreviousInteraction(group, dreamiId)`가 해당 그룹의 오퍼 이력에서만 찾기 때문이다.

### 손실의 형태

살아 있는 1명이 5초 만에 수락하면 그 주문 자체는 멀쩡히 끝난다. `moveGroupToWaitingIfExhausted`는 **살아 있는 오퍼가 하나도 없을 때만** 방을 `WAITING`으로 되돌린다. 그래서 피해는 두 가지로 나뉜다.

1. **기회비용** — 슬롯 3개 중 2개가 응답 불가능한 사람에게 묶인다. 그 자리에 갈 수 있었던 실제 대기 드리미가 이번 라운드에서 밀린다.
2. **최악의 경우 30초 전소** — 3명이 전부 유령이면 아무도 응답하지 않고, 30초를 다 태운 뒤 `closeForRematch()` → `WAITING` → 다음 배치 윈도(`matching.batch-window=200ms`)를 기다린다.

## 결정

**후보 선정 입력에서 SSE 연결이 살아 있는 드리미만 남긴다.**

오퍼 생성 경로가 두 개이므로 양쪽 모두에 적용한다.

| 경로 | 진입점 | 적용 방식 |
| --- | --- | --- |
| 배치 매칭 사이클 | `applyRunMatchingAssignmentCycle()` | `assemble()`에 넘기는 대기 드리미 목록을 `reachableWaitingDreamis()`로 교체 |
| fallback 재매칭 스캔 | `attemptOfferRound()` | candidates 스트림에 `.filter(this::isReachable)` 추가 |

```java
private boolean isReachable(WaitingDreami dreami) {
    if (notificationService.isReachableNow(dreami.dreamiId())) {
        return true;
    }
    meterRegistry.counter("matching.candidates.filtered", "reason", "not_connected").increment();
    return false;
}
```

판정은 `NotificationService.isReachableNow` → `SseService.isConnected` → `SseEmitterRegistry.isConnected`로 내려가는 `ConcurrentHashMap` 조회 한 번이다. 이 코드는 매칭 엔진의 **단일 writer 스레드** 위에서 도는데, 락도 블로킹 I/O도 없으므로 엔진을 멈추지 않는다.

## 왜 이렇게 했는가

### 기준이 "푸시 구독 보유"가 아니라 "살아 있는 SSE 연결"인 이유

웹푸시를 붙여도 이 계산은 바뀌지 않는다. 푸시 수신 → 잠금 해제 → 앱 열기 → 로딩 → 수락을 **30초 안에** 끝내는 일은 사실상 없다. 게다가 Android Doze는 일반 우선순위 메시지를 수 분까지 붙잡고, iOS는 알림 예산으로 스로틀한다.

푸시 구독이 있다는 이유로 오퍼를 보내면 유령 문제가 그대로 재현되면서 "알림을 받고 눌렀는데 이미 만료됨"이라는 더 나쁜 경험까지 추가된다.

### `MatchingEligibilityPolicy`에 넣지 않은 이유

그 인터페이스의 계약 Javadoc이 **결정성을 명시적으로 요구한다** — "시스템 시각을 직접 조회하지 않는다 / 같은 candidate와 evaluatedAt이 주어지면 항상 같은 결과를 반환한다".

SSE 연결 상태는 외부 가변 상태라 이 계약을 깬다. 더 실질적인 문제로, `MatchingPlanValidator.validate`가 산출된 plan에 대해 eligibility를 **재실행**한다. 비결정적 정책을 넣으면 두 호출 사이에 연결이 끊긴 순간 **할당 정책이 방금 만든 plan을 validator가 거부**한다.

`assemble()` 입력에서 걸러야 problem · policy · validator가 모두 같은 전제 위에서 돈다. 걸러진 드리미는 problem에 **아예 등장하지 않는다.**

### 필터가 `limit(3)` 앞에 있어야 하는 이유

```java
List<WaitingDreami> candidates = dreamiMap.values().stream()
        .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
        .filter(dreami -> !excludedDreamiIds.contains(dreami.dreamiId()))
        .filter(this::isReachable)          // ← limit 앞
        .sorted(orderingComparator())
        .limit(MAX_OFFER_COUNT)
        .toList();
```

뒤에 두면 "3명 뽑고 2명 버려서 1명만 남는" 결과가 된다. 앞에 둬야 유령을 제외한 풀에서 3명을 채워 **슬롯이 온전히 살아 있는 드리미로 찬다.**

### SSE 연결이 끊길 때 `dreamiMap`에서 제거하지 않은 이유

더 깔끔해 보이지만 실제로는 훨씬 복잡하다. 모바일 `EventSource`는 백그라운드 전환·네트워크 핸드오버로 **끊임없이 재연결**한다. 끊김 즉시 제거하면 지하철에서 나올 때마다 드리미가 대기열에서 빠지고 대기 시간이 초기화된다.

제대로 하려면 유예 기간(~60초) + 재연결 시 제거 취소 경로 + 그 타이머의 생명주기 관리가 필요하다. 필터는 그 복잡도 없이 이득의 대부분을 가져가고, 판정이 순간적으로 틀려도 **우아하게 열화**된다(다음 배치 윈도에서 다시 후보가 된다).

필터가 불충분하다고 증명되면 그때 재검토한다.

## 한계

**`isConnected`는 "레지스트리에 emitter가 있는가"만 본다.** TCP가 조용히 죽었지만 아직 감지되지 않은 연결은 살아 있는 것으로 판정된다. 실제 정리는 `sse.heartbeat-interval=25s` 주기의 heartbeat 쓰기가 실패할 때 일어나므로, **최대 25초의 감지 지연**이 있다.

즉 이 필터는 유령을 전부 막지 못하고, "이미 정리됐거나 명시적으로 닫힌 연결"을 막는다. 그래도 앱 스와이프 종료·탭 종료는 브라우저가 소켓을 닫으므로 대부분 즉시 걸러진다.

반대 방향 오차(재연결 중인 정상 드리미가 잠깐 걸러짐)는 다음 배치 윈도(200ms)에서 회복되므로 무해하다.

## 지표

`matching.candidates.filtered{reason=not_connected}` — 필터가 실제로 몇 명의 유령을 막았는지. 기존 Grafana에서 `sse.events.dropped{reason=not_connected}`와 함께 보면 효과가 드러난다.

## 검증

단위 테스트 2개 (`MatchingServiceTest`):

| 테스트 | 검증 대상 |
| --- | --- |
| `SSE_연결이_없는_드리미는_오퍼_후보에서_제외된다` | fallback 스캔 경로. 유령은 오퍼를 받지 않고 `MATCHING`에 머문다(`PROPOSED` 전이 없음 = 30초 TTL 소각 없음) |
| `배치_사이클은_SSE_연결이_없는_드리미를_할당_문제_입력에서_제외한다` | 배치 경로. `ArgumentCaptor`로 assembler에 넘어간 목록을 직접 확인 |

부하 하니스(`matchingtest/run.mjs`) 전후 비교에서 아래가 모두 만족해야 한다.

- `sse.events.dropped{reason=not_connected}` 감소
- `matching.candidates.filtered{reason=not_connected}` 증가
- 매칭 성공까지의 라운드 수 감소

> 하니스는 `modules/drive.mjs`에서 드리미가 **SSE 구독을 마친 뒤** 온라인 전환을 하므로(`── 3. 드리미 SSE + 온라인 ──`), 이 필터 때문에 부하 테스트가 무력화되지는 않는다.

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| `domain/matching/service/MatchingService.java` | `isReachable`, `reachableWaitingDreamis`, 두 경로의 필터 적용 |
| `global/notification/NotificationService.java` | `isReachableNow` — 도메인이 보는 유일한 진입점 |
| `global/sse/SseEmitterRegistry.java` | `isConnected`, heartbeat 기반 유령 연결 정리 |
| `domain/matching/policy/assignment/MatchingAssignmentProblemAssembler.java` | 필터가 적용되는 배치 경로 입력 |
| `domain/matching/policy/eligibility/OutcomeCooldownOfferPolicy.java` | 만료 후 쿨다운 — 유령이 유발하던 2차 피해 |
