# SSE·단일 로그인 세션 정책

[sse-current-behavior.md](sse-current-behavior.md)에서 정리한 현재 동작을 반영해, 1 ActiveSession–1 SSE 연결 구조로 정책을 갱신한다. 이전 버전(사용자당 연결 5개 상한·204 거부)은 폐기됐다.

## 계정 구조

    userId
    └─ ActiveSession 0..1
       ├─ LoginSession
       └─ SseConnection 0..1

- 계정(userId)당 활성 로그인 세션(`ActiveSession`)은 최대 1개다. 새 로그인이 성공하면 같은 계정의 이전 `ActiveSession`과 그 안의 `SseConnection`이 함께 제거된다(`REPLACED_BY_LOGIN`).
- 활성 세션당 서버 SSE 연결(emitter)은 최대 1개다. 같은 세션 안에서 여러 탭을 열어도 서버가 실제로 들고 있는 연결은 하나다.
- 세션 ID는 `ActiveSessionRegistry` 내부에서 현재 세션 판정(치환·비교)에만 쓰고, 로그·메트릭·이벤트 payload에는 남기지 않는다.

## 브라우저 탭 구조

    여러 탭
    └─ Web Lock leader 1개
       └─ EventSource 1개
          └─ BroadcastChannel로 이벤트 공유

- 탭(문서 인스턴스)마다 메모리 `peerId`(`crypto.randomUUID()`)로만 식별한다. 서버 registry의 key로 쓰지 않으므로 탭 복제로 값이 겹치는 문제가 없다.
- 모든 탭이 같은 이름의 Web Lock(`naengsam:sse-lock:{userId}`)을 exclusive로 요청한다. `ifAvailable`로 포기하지 않고 대기시키므로, 대표 탭이 종료되면(lock 해제) 대기 중이던 다음 탭이 자동으로 승계한다.
- 실제 `EventSource`는 Web Lock을 획득한 대표 탭만 만든다. 나머지 탭은 BroadcastChannel(`naengsam:sse-channel:{userId}`)로 대표 탭의 이벤트·상태를 받고, 자신의 구독 이벤트 이름을 대표 탭에 전달한다.
- BroadcastChannel 메시지 종류: `leader-ready`(epoch 포함), `subscriptions`, `event`, `status`, `reconnect-request`, `peer-closing`, `session-invalid`. `peer-closing`은 전달 보장이 없으므로 정합성을 여기에 의존하지 않는다 — stale 구독이 잠시 남는 것은 허용하고, 대표 교체 시 원격 구독 맵을 초기화해 살아있는 탭들의 재전송으로 수렴시킨다.

## 종료·복구 정책

- **새 구독은 즉시 교체한다.** 기존 emitter가 있어도 거부(204)하지 않고 원자적으로 교체하며, 이전 emitter는 registry 연산이 끝난 뒤 종료한다.
- **정상 종료는 connectionId로 보호된 disconnect다.** 로그아웃·명시적 재연결·대표 탭 handoff·페이지 종료에서는 `EventSource.close()` → `connectionId`가 있으면 `sendBeacon(/api/v1/sse/disconnect?connectionId=...)` → 상태 정리 → lock 반환 순으로 처리한다. userId·sessionId·connectionId가 모두 일치할 때만 실제로 지워지므로, 이전 페이지의 늦은 요청이 새 연결을 건드리지 못한다.
- **비정상 종료는 heartbeat가 최종 회수한다.** 탭 강제 종료·네트워크 완전 단절처럼 정상 종료 신호를 보낼 수 없는 경우에만 heartbeat 실패로 정리된다.
- **heartbeat 주기는 25초로 유지한다.** 재연결 UX 문제를 heartbeat 단축으로 풀지 않는다 — 새 구독의 즉시 replace가 그 역할을 한다.
- **새로고침·재실행은 heartbeat를 기다리지 않는다.** 모든 탭을 닫았다 다시 열어도, 새 구독이 (아직 heartbeat로 청소되지 않았을) 이전 emitter를 즉시 교체한다.
- **사용자당 연결 5개 상한과 204 응답은 폐기했다.** 새 구독은 항상 emitter를 반환한다.
- **이벤트 replay는 없다.** `Last-Event-ID`나 이벤트 저장소가 없으므로, `connected` 이벤트 수신 직후 화면은 REST snapshot 조회로 상태를 다시 맞춘다(예: `syncCurrentMatching`, `refreshUser`). 이는 대표 탭과 follower 탭 모두 동일하게 수행한다.
- **세션 무효화는 모든 탭에 전파한다.** 다른 곳에서 로그인해 서버가 emitter를 끊으면, 대표 탭이 `/me` 세션 프로브로 확인한 뒤 axios 인터셉터의 기존 강제 로그아웃 흐름을 태우고, 같은 브라우저의 다른 탭에는 `session-invalid` 메시지로 알려 전부 정리되게 한다.

## 다중 서버 환경의 한계 (현재 미보장)

- 이 정책은 인메모리 `ActiveSessionRegistry`(JVM 로컬 `ConcurrentHashMap`)로만 구현돼 있다. 서버가 2대 이상이 되면 노드마다 별도의 registry를 가지므로, **현재 구조로는 전역 단일 세션·전역 단일 SSE 연결을 보장하지 못한다** — 다른 노드에 로그인해도 이 노드의 이전 세션을 찾아 무효화할 수 없고, 이벤트가 발생한 노드에 그 사용자의 emitter가 없으면 전달되지 않는다.
- 다중 서버로 전환할 때는 Redis 등을 이용한 **분산 session ownership**(활성 세션의 원자적 치환·비교, 예: `SET`/Lua)과 **event routing**(발생 노드 → emitter가 있는 노드로 이벤트를 옮기는 Pub/Sub 등)이 별도로 필요하다.
- 상세 확장 전략은 [multi-server-scaling.md](multi-server-scaling.md)에 정리돼 있다. 다만 그 문서는 이번 리팩터(1 ActiveSession–1 SseConnection, `SseEmitterRegistry` → `SseConnectionManager`, 연결 상한 폐기) 이전 용어 기준으로 쓰여 있어 별도 갱신이 필요하다.

## 관련 구현

- 백엔드: `ActiveSessionRegistry`(단일 진실 공급원), `SseConnectionManager`(연결 lifecycle·전송), `SseController`(`/api/v1/sse/subscribe`, `/api/v1/sse/disconnect`), `LoginCheckInterceptor`(현재 세션 검증).
- 프론트: `SseTabCoordinator`(Web Lock + BroadcastChannel 탭 조정), `SseProvider`(계정당 실제 연결을 대표 탭에만 두는 React 배선).
