# 단일 세션·SSE의 다중 서버 확장 전략

[session-sse-policy.md](session-sse-policy.md)에서 결정한 정책(사용자당 활성 세션 1개, 로그아웃·만료 시 `disconnectAll(userId)`, 사용자당 연결 상한)과 [sse-current-behavior.md](sse-current-behavior.md)의 현재 동작을 전제로, **서버를 여러 대로 늘릴 때** 무엇이 깨지고 무엇이 필요한지 정리한다.

## 지금 보장하는 것과 그 한계

현재 구현은 **단일 서버 안에서만** 아래를 보장한다.

- 같은 사용자의 활성 `HttpSession`은 항상 하나다. 새 로그인 시 이전 디바이스의 세션과 SSE가 응답 전에 종료된다(`ActiveSessionRegistry.replace` → `disconnectAll(REPLACED_BY_LOGIN)` → 이전 세션 `invalidate`).
- 같은 브라우저의 여러 탭은 허용하고, 탭당 `EventSource`는 하나다. 사용자당 connection은 최대 5개다(초과 시 204로 거부, 기존 연결은 유지).
- 로그아웃·세션 timeout 시 사용자 emitter 전체를 종료한다. heartbeat 실패는 해당 connection만 제거한다.
- SSE 장애 중에는 매칭 상태를 polling으로 복구하고, 재연결 직후 즉시 동기화한다([sse-current-behavior.md](sse-current-behavior.md) 4절의 유실 문제에 대한 보완).
- 세션 ID는 SSE registry·로그·메트릭 어디에도 노출되지 않는다. registry는 `userId`(UUID)만 키로 쓰고, 연결 식별은 서버 내부 `connectionId`(랜덤 UUID)로 한다.

이 보장들은 모두 **JVM 로컬 상태**(`ConcurrentHashMap`)와 **Tomcat 인메모리 세션**에 의존한다. 서버가 2대 이상이 되면 다음이 깨진다.

| 상태 | 현재 위치 | 다중 서버에서의 문제 |
| --- | --- | --- |
| 로그인 세션 | Tomcat 인메모리 | 노드마다 세션이 따로 존재해 같은 사용자가 노드별로 동시 로그인 가능 |
| 활성 세션 인덱스 | `ActiveSessionRegistry`(per-JVM Map) | 새 로그인이 다른 노드의 이전 세션을 못 찾아 강제 로그아웃이 안 됨 |
| SSE emitter | `SseEmitterRegistry`(per-JVM Map) | 이벤트를 발생시킨 노드에 그 사용자의 emitter가 없으면 전달 실패 |
| 매칭 인메모리 상태 | `MatchingService`(단일 엔진 스레드 소유) | 노드마다 상태가 갈라져 매칭 결과가 어긋남 |

## 확장 전략

### 1. `ActiveSessionRegistry` → Spring Session Redis

세션 저장소와 활성 세션 인덱스를 외부화한다.

- 세션 자체는 **Spring Session Redis**로 옮겨 모든 노드가 같은 세션을 공유한다(현재 쿠키는 `SameSite=None; Secure`이므로 크로스 노드 공유와 호환).
- "사용자당 활성 세션 1개"는 Redis에 `active-session:{userId} → sessionId` 키로 두고, 새 로그인에서 **원자적 치환**(`SET`/`GETSET`, 또는 Lua)으로 이전 sessionId를 얻어 무효화한다. 이는 지금 `replace`의 last-write-wins 의미를 그대로 옮긴 것이다.
- `removeIfCurrent`의 "현재 세션일 때만 제거"(stale timeout 가드)는 Redis에서 `sessionId` 비교 후 삭제(compare-and-delete)로 대체한다. 세션 만료는 `SessionExpirationListener` 대신 **Redis key 만료 이벤트**(keyspace notification) 또는 Spring Session의 만료 이벤트로 받는다.

### 2. emitter는 각 서버 로컬 유지

`SseEmitter`는 특정 노드의 열린 HTTP 응답에 묶여 있어 **직렬화·이전이 불가능**하다. 따라서 `SseEmitterRegistry`는 지금처럼 **노드 로컬**로 둔다. 클라이언트의 SSE 연결은 로드밸런서를 통해 특정 노드에 붙고, 그 노드의 로컬 Map에만 존재한다. 다중 서버 확장은 emitter를 공유하는 것이 아니라 **이벤트를 올바른 노드로 보내는** 문제로 바뀐다(→ 3).

### 3. 이벤트 전파 → Redis Pub/Sub

한 노드에서 발생한 이벤트를 그 사용자의 emitter가 있는 노드로 전달해야 한다.

- 도메인은 지금처럼 `SseService.send(userId, type, payload)`만 호출한다. 이 지점을 **Redis Pub/Sub publish**로 바꾸고, 각 노드는 채널을 구독해 수신 시 **자기 로컬 registry에 있는** 해당 `userId` emitter에게만 전달한다(없으면 무시).
- `disconnectAll(userId, reason)`도 노드 간 전파가 필요하다. A 노드의 로그인이 B 노드에 붙어 있는 이전 디바이스의 SSE를 끊어야 하기 때문이다. 종료 신호(`SseCloseReason` 포함)를 같은 Pub/Sub로 브로드캐스트해 각 노드가 로컬 emitter를 정리한다.
- 전송을 오프로딩하는 단일 가상 스레드(`sse-sender`)의 순서 보장은 **노드 로컬 순서**만 보장하게 된다. 전역 순서가 필요하면 이벤트에 시퀀스를 넣거나, 순서 민감 이벤트는 스냅샷 재조회(→ 4)로 수렴시킨다.

### 4. 상태 복구 → 매칭 snapshot API

Pub/Sub는 at-most-once에 가깝다(구독 중이 아닐 때 발행된 메시지는 유실). `Last-Event-ID`/replay가 없는 현재 구조에서는 **연결이 서면 상태를 다시 조회**하는 방식으로 유실을 흡수한다.

- 이미 구현된 `GET /api/v1/matching/current`(매칭 snapshot)를 재연결 직후·polling으로 호출해 팝업 상태를 서버 기준으로 맞춘다. 이 복구 경로는 노드 수와 무관하게 동작하므로, 다중 서버에서도 이벤트 전파의 안전망이 된다.
- 확장 시에는 매칭 외 도메인(배달 등)도 같은 형태의 snapshot 조회를 갖추는 것이 바람직하다.

### 5. 매칭 인메모리 상태의 외부화 또는 단일 소유권

`MatchingService`는 상태를 인메모리에 두고 **단일 엔진 스레드**가 변경, 조회 스레드가 읽는 구조다(단일 서버 전제의 동시성 모델). 다중 서버에서는 둘 중 하나가 필요하다.

- **외부화**: 매칭 상태(진행 중 offer·그룹)를 Redis 등 공유 저장소로 옮기고, 변경을 원자 연산/락으로 직렬화한다.
- **단일 소유권**: 매칭 엔진을 한 노드(리더)만 소유하도록 하고(리더 선출), 다른 노드는 요청을 리더로 위임한다. 인메모리 모델을 유지하면서 상태 분기를 막는 가장 작은 변경이다.

## 유지해야 하는 불변식

확장 후에도 다음은 반드시 유지한다.

- **세션 ID 비노출**: SSE registry는 `userId`만, 연결 식별은 내부 `connectionId`만 쓴다. sessionId는 활성 세션 치환·비교 용도로만 서버 내부에 두고 로그·메트릭·이벤트 payload에 남기지 않는다(`ActiveSession`·`LoginSession` 주석의 설계 근거).
- **사용자당 활성 세션 1개 / 여러 탭 허용 / 탭당 EventSource 1개 / 사용자당 connection ≤5**: 세션 인덱스와 연결 상한 검사가 노드 로컬에서 Redis 기반으로 옮겨가더라도 의미는 동일해야 한다. 특히 연결 상한은 "사용자당"이므로 다중 서버에서는 노드 로컬 카운트가 아니라 **전역 카운트**(Redis)로 검사해야 정확하다.
