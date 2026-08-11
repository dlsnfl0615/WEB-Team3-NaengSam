# SSE 현재 동작과 확인 사항

## 요약

| 상황 | 현재 동작 | 판정 |
| --- | --- | --- |
| 같은 사용자의 여러 탭·재연결 | 구독마다 `connectionId`를 발급하고 모든 emitter에 broadcast | 지원 |
| 정상적인 component unmount | `useSse` cleanup에서 `EventSource.close()` 호출 | 지원 |
| 탭·브라우저 강제 종료 | 다음 전송 실패 또는 1시간 timeout 때 정리될 수 있음 | 부분 지원 |
| 네트워크 단절 | 브라우저 `EventSource`의 자동 재연결에 의존 | 실환경 확인 필요 |
| focus out | 연결을 끊는 로직 없음 | 유지 예상 |
| 로그아웃 | 현재 탭의 프론트 연결은 닫히지만, 서버는 세션만 무효화 | 보완 필요 |
| 이벤트 유실 복구 | event ID·`Last-Event-ID`·replay 없음 | 미지원 |
| 다중 서버 | emitter가 각 JVM 메모리에만 존재 | 미지원 |

## 1. 연결 생성과 중복

- 같은 UID로 여러 번 구독하면 emitter가 모두 유지된다.
- 탭 복제·새로고침·`EventSource` 재연결은 서로 다른 `connectionId`를 받는다.
- 한 화면에서도 전역 `MatchingPopup`과 배달 화면의 `useSse`가 동시에 열릴 수 있다. 이는 현재 구조상 정상이다.
- React `StrictMode`의 개발 환경 effect 재실행은 순간적으로 연결을 추가할 수 있다. cleanup은 있지만 서버의 즉시 정리까지는 보장하지 않는다.
- 사용자당 최대 연결 수 제한은 없다.

## 2. 연결 종료와 탭 상태

- route 이동, component unmount, `enabled: false`로의 변경은 `source.close()`를 호출한다.
- 단순 focus out이나 `visibilityState=hidden`을 처리하는 코드는 없으므로 연결은 유지된다.
- 모바일 background·tab freeze·discard·BFCache는 브라우저 정책에 따라 다르며 별도 처리가 없다.
- 브라우저 강제 종료나 무패킷 네트워크 단절은 서버가 즉시 알지 못할 수 있다.
- 서버는 `onCompletion`, `onTimeout`, `onError`, `send_failed`에서 연결을 제거한다. heartbeat는 없고 timeout은 1시간이다.
- 하나의 전송 실패는 해당 emitter만 제거하며, 같은 사용자의 다른 연결은 유지한다.

## 3. 로그아웃과 세션 만료

- 현재 탭의 로그아웃은 `isAuthenticated=false`를 만들어 전역 `useSse`를 정리한다.
- 로그아웃 API는 `HttpSession.invalidate()`만 호출하고 registry의 emitter는 직접 종료하지 않는다.
- SSE는 최초 요청에서만 인증한다. 이미 열린 stream에는 세션 무효화 후 인터셉터가 다시 실행되지 않는다.
- 다른 탭은 로그아웃 상태를 즉시 공유받지 못한다. 다음 API 401, 전송 실패 또는 timeout 전까지 기존 stream이 남을 수 있다.
- 세션 timeout·계정 정지·회원 탈퇴와 emitter 종료를 연결하는 로직은 없다.

> 현재 정책에서는 로그아웃 시 `disconnectAll(userId)`를 호출하는 방식을 우선 검토할 수 있다. 여러 디바이스의 세션별 종료가 필요해지면 emitter에 session 소유 정보도 보관해야 한다.

## 4. 재연결과 이벤트 유실

- 재연결은 native `EventSource`에 의존하며 백엔드는 `connected` 이벤트만 보낸다.
- SSE event ID, `Last-Event-ID`, 이벤트 저장소, replay는 구현되지 않았다.
- 연결이 끊긴 동안의 이벤트는 유실될 수 있다. `connected` 수신 후 공통적으로 최신 상태를 재조회하는 로직도 없다.
- 배달 화면은 진입 시 REST로 상태를 조회하지만, 모든 매칭·배달 이벤트의 유실을 공통적으로 복구하는 정책은 없다.
- 따라서 SSE를 단순 알림으로 볼지, 업무 상태의 유일한 전달 수단으로 볼지를 결정해야 한다.

## 5. 서버 운영과 자원

- emitter는 현재 JVM의 `ConcurrentHashMap`에만 저장된다. 다중 인스턴스 간 이벤트 전파는 지원하지 않는다.
- 서버 재시작 시 emitter는 모두 사라지며, 클라이언트의 자동 재연결에 의존한다.
- graceful shutdown은 sender executor에 `shutdown()`만 호출하고 emitter를 명시적으로 complete하지 않는다.
- 하나의 단일 가상 sender 스레드가 모든 이벤트와 emitter를 순차적으로 전송한다. 순서는 유지되지만 느린 연결이나 대량 fan-out은 적체를 만들 수 있다.
- heartbeat, 사용자당 연결 제한, 재연결 backoff 제어는 없다.

## 6. 관측과 테스트

현재 메트릭은 다음을 제공한다.

- 현재 연결 수
- 누적 연결·종료 수
- event별 전송 성공 수
- 미연결·전송 실패 drop 수

사용자 수와 emitter 수의 분리, 사용자당 연결 분포, 재연결 횟수는 집계하지 않는다. heartbeat가 없으므로 active Gauge는 실제보다 클 수 있다.

단위 테스트로는 다중 연결, broadcast, 사용자 격리, 전송 실패 격리, 마지막 연결 정리, 동시 연결 등록, 메트릭을 검증한다. 다음은 실제 브라우저·인프라 검증이 필요하다.

1. 같은 브라우저의 두 탭이 모두 이벤트를 받는지
2. 탭 종료·브라우저 강제 종료·네트워크 단절 후 정리 시점
3. 다른 탭에서 로그아웃했을 때 기존 stream이 이벤트를 받는지
4. focus out·모바일 background·절전 후 재연결과 이벤트 유실 여부
5. 서버 재시작·배포 후 자동 재연결 여부

## 우선 결정 필요

1. 로그아웃·세션 만료 시 서버가 emitter를 즉시 종료할지
2. 연결 중단 동안 놓친 매칭·배달 이벤트를 어떻게 복구할지
3. heartbeat와 사용자당 최대 연결 수를 도입할지
4. 다중 서버 전환 시 Redis Pub/Sub 등으로 이벤트를 전파할지
