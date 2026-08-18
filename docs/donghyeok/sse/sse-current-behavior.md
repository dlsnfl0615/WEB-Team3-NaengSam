# SSE 현재 동작

[session-sse-policy.md](session-sse-policy.md)에서 결정한 1 ActiveSession–1 SseConnection 정책이 실제로 구현된 뒤의 현재 동작을 정리한다. 이전 판(사용자당 최대 5개 연결·204 거부·heartbeat 없음)은 폐기됐다.

## 요약

| 상황 | 현재 동작 | 판정 |
| --- | --- | --- |
| 같은 사용자의 여러 탭 | Web Lock으로 뽑힌 대표 탭 하나만 서버 `EventSource`를 소유, 나머지는 BroadcastChannel로 이벤트를 받음 | 지원 |
| 탭 새로고침·재연결 | 새 구독이 기존 emitter를 즉시 교체(replace), 204 없음 | 지원 |
| 정상적인 component unmount | `useSse` cleanup에서 handler만 해제(연결 자체는 유지) | 지원 |
| 대표 탭 종료(로그아웃 등) | `EventSource.close()` → connectionId 일치 시 `sendBeacon(disconnect)` → lock 반환, follower가 자동 승계 | 지원 |
| 탭·브라우저 강제 종료 | 정상 종료 신호를 못 보내므로 heartbeat(25초 주기) 실패로 최종 회수 | 지원 |
| 네트워크 단절 | 일시 장애는 native `EventSource` 자동 재연결 + 프론트 polling fallback, 영구 종료는 `/me` 세션 프로브 | 지원 |
| 로그아웃 | 서버가 `ActiveSession`과 `SseConnection`을 함께 제거하고 emitter를 종료, 같은 브라우저의 다른 탭도 상태 정리 | 지원 |
| 다른 곳에서 로그인 | 이전 `ActiveSession`·SSE를 서버가 즉시 교체, 이전 브라우저는 `/me` 401로 강제 로그아웃 후 다른 탭에도 전파 | 지원 |
| 이벤트 유실 복구 | event ID·`Last-Event-ID`·replay 없음. `connected` 수신 시 REST snapshot 재조회로 보완 | 부분 지원(정책으로 흡수) |
| 다중 서버 | `ActiveSessionRegistry`·emitter가 각 JVM 메모리에만 존재 | 미지원 |

## 1. 연결 소유권과 탭 구조

- 서버는 계정(userId)당 `ActiveSession`을 최대 1개, 그 안에 `SseConnection`을 최대 1개만 둔다(`ActiveSessionRegistry`).
- 브라우저는 계정당 Web Lock(`naengsam:sse-lock:{userId}`) 하나를 여러 탭이 경합해, 획득한 대표 탭만 실제 `EventSource`를 연다(`SseTabCoordinator`). 나머지 탭은 서버 연결을 만들지 않는다.
- 탭 식별은 서버 registry key가 아니라 탭(문서 인스턴스)마다 만드는 메모리 `peerId`다. 탭 복제로 값이 겹치는 문제가 없다.
- React `StrictMode`의 개발 환경 effect 재실행(mount→unmount→mount)이 순간적으로 lock을 두 번 요청할 수 있지만, 첫 요청은 대기 중 상태에서 `AbortController`로 취소되므로 최종적으로 owner는 하나다.

## 2. 연결 종료와 재연결

- 새 구독(subscribe)은 기존 emitter가 있어도 거부하지 않고 즉시 교체한다. 사용자당 연결 상한과 204 응답은 없다.
- 정상 종료(로그아웃, 명시적 재연결, 대표 탭 handoff, 페이지 종료 `pagehide`)는 `EventSource.close()` → connectionId가 있으면 `sendBeacon`으로 `/api/v1/sse/disconnect` 호출 → 상태 초기화 → lock 반환 순서로 처리한다. userId·sessionId·connectionId가 모두 일치할 때만 제거되므로, 이전 페이지의 늦은 disconnect 요청이 새 연결을 지우지 못한다.
- 일시적 `onerror`(readyState가 CLOSED가 아님)에서는 disconnect beacon을 보내지 않고 native `EventSource`의 자동 재연결을 그대로 둔다. 그동안 매칭 상태는 프론트 polling으로 보완한다(`reconnecting` 상태).
- 영구 종료(readyState=CLOSED)는 서버 쪽 세션이 이미 무효화된 경우가 대부분이라(다른 곳에서 로그인), 대표 탭이 `/me`로 확인해 실제 무효면 axios 인터셉터가 강제 로그아웃까지 이어간다.
- 브라우저 강제 종료나 완전한 네트워크 단절처럼 정상 종료 신호를 보낼 수 없는 경우만 heartbeat(25초 주기) 실패로 서버가 최종 정리한다. 새로고침·재실행은 heartbeat를 기다리지 않고 새 구독의 즉시 replace로 처리된다.
- 하나의 계정에는 연결이 하나뿐이므로 "같은 사용자의 다른 연결에 영향 없음"은 더 이상 의미가 없다 — 대신 "다른 계정에는 영향 없음"이 격리 기준이다.

## 3. 로그아웃·세션 만료·다른 곳에서 로그인

- 로그아웃은 `ActiveSessionRegistry.removeIfCurrent(sessionId)`로 `ActiveSession`(과 그 안의 `SseConnection`)을 제거한 뒤, 꺼낸 연결을 `SseConnectionManager.close(..., LOGOUT)`로 종료한다.
- 세션 timeout은 `SessionExpirationListener`가 동일한 방식으로 처리하며, 이미 로그아웃/재로그인으로 교체된 이전 세션의 늦은 만료 콜백은 `removeIfCurrent`의 sessionId 일치 검사로 걸러진다.
- 다른 곳에서 로그인하면 `UserController`가 `activeSessionRegistry.replace()`로 이전 `ActiveSession`을 즉시 교체하고, 이전 SSE 연결을 `REPLACED_BY_LOGIN` 사유로 종료한다. 이전 브라우저의 `EventSource`는 재연결을 시도하지만 그 servlet 세션은 더 이상 현재 `ActiveSession`이 아니므로 `LoginCheckInterceptor`가 401을 반환한다.
- 이전 브라우저의 대표 탭은 이 401(정확히는 영구 종료 후 `/me` 프로브 실패)을 감지해 축의(axios) 인터셉터의 기존 강제 로그아웃 흐름을 태우고, 같은 브라우저의 다른 탭에는 `session-invalid` BroadcastChannel 메시지로 알려 전부 인증 상태를 정리하게 한다.

## 4. 재연결과 이벤트 유실

- 재연결은 native `EventSource`에 의존하며, 서버는 `connected` 이벤트에 새 `connectionId`를 실어 보낸다.
- SSE event ID, `Last-Event-ID`, 이벤트 저장소, replay는 여전히 구현하지 않았다 — BroadcastChannel도 과거 이벤트를 재생하지 않는다.
- 대신 `connected` 전이(최초 연결·재연결·대표 탭 승계로 인한 새 연결 모두 포함)마다 대표 탭과 follower 탭이 공통으로 REST snapshot을 다시 조회한다(`syncCurrentMatching`, `refreshUser`). 연결이 끊긴 동안의 이벤트 유실을 이 지점에서 흡수한다.
- 매칭 외 도메인(배달 등)은 화면 진입 시 REST로 상태를 조회하는 기존 패턴을 그대로 유지한다 — 모든 이벤트 유실을 공통으로 복구하는 범용 장치는 아직 없다.

## 5. 서버 운영과 자원

- `SseConnection`(emitter)은 `ActiveSessionRegistry`가 `ActiveSession`의 슬롯으로 들고 있으며, 여전히 JVM 로컬 메모리에만 존재한다. 다중 인스턴스 간 이벤트 전파는 지원하지 않는다.
- 서버 재시작 시 `ActiveSession`·emitter가 모두 사라지며, 클라이언트의 자동 재연결(또는 새 구독의 즉시 replace)에 의존한다.
- 단일 가상 sender 스레드(`sse-sender`)가 이벤트를 순차 전송하는 구조는 그대로다.
- heartbeat 주기는 25초로 고정, 사용자당 연결 상한은 폐기했으므로 더 이상 존재하지 않는다.

## 6. 관측과 테스트

메트릭은 다음과 같이 갱신됐다.

- `sse.connections.active`: 이제 "SSE 연결을 가진 `ActiveSession` 수"를 집계한다(연결이 계정당 최대 1개이므로 곧 활성 계정 SSE 수와 같다).
- `sse.connections.opened` / `sse.connections.closed{reason}`: `reason`에 `replaced`, `client_disconnect`가 새로 추가됐고 `completion`/`timeout`/`error`/`send_failed`/`heartbeat_failed`/`logout`/`session_expired`/`replaced_by_login`도 그대로 집계된다.
- `sse.connections.rejected`: 연결 상한이 없어졌으므로 더 이상 존재하지 않는다.
- `sse.events.sent{event}` / `sse.events.dropped{reason}`: 이벤트별 전송/드롭 집계는 그대로다.

단위 테스트는 다음을 검증한다: 같은 계정의 두 번째 연결이 첫 emitter를 원자적으로 교체, 교체 후 active Gauge와 replaced 메트릭, 다른 계정과의 격리, 현재 연결에만 전송, 이전 emitter의 늦은 completion/send failure/heartbeat 실패가 새 연결을 건드리지 않음, 늦은 disconnect 요청의 idempotent 처리, 이전 sessionId로 구독 불가, 로그인/로그아웃 시 SSE 정리, `connected` payload의 `connectionId` 포함(백엔드), Web Lock 승계·follower 구독 전달·leader-ready 재전송·StrictMode 단일 owner·stale leader 메시지 방어(프론트 `SseTabCoordinator`), 대표 탭만 EventSource 소유·follower REST 동기화·disconnect beacon·세션 무효화 전파(프론트 `SseProvider`).

다음은 여전히 실제 브라우저·인프라 검증이 필요하다(아래 "최종 수동 검증 시나리오" 참고).

1. 같은 탭에서 heartbeat 주기(25초) 안에 반복 새로고침해도 204·모달이 뜨지 않는지
2. 탭 2개 이상에서 대표 탭 종료 시 승계가 사용자 조작 없이 매끄러운지
3. 다른 브라우저에서 로그인했을 때 이전 브라우저의 모든 탭이 실제로 강제 로그아웃되는지
4. 서버 재시작·배포 후 자동 재연결 여부

## 우선 결정 필요

1. **다중 서버 전환**: 현재는 `ActiveSessionRegistry`·emitter가 JVM 로컬이라 전역 단일 세션·SSE를 보장하지 못한다. Redis 기반 분산 session ownership과 event routing(Pub/Sub 등)이 필요하며, 상세 방향은 [multi-server-scaling.md](multi-server-scaling.md)에 있다(다만 그 문서는 이번 리팩터 이전 용어 기준이라 갱신이 필요하다).
