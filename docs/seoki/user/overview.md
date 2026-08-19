# User 도메인

회원가입·로그인·세션·문자인증·역할 전환 가드를 담당한다.
**엔티티가 없는 도메인**이다 — 영속 대상은 `domain/boormi/entity/Boormi`(테이블 `BOORMI`)이고, 이 패키지는 그 위에 인증 정책만 얹는다. `domain/user/entity`에 있는 것은 상태코드 enum `UserCd`(ACTIVE / DELETED / RESTRICTED / BANNED) 하나뿐이다.

인증 인프라(`@LoginUser`, 인터셉터, 세션 레지스트리)는 이 도메인이 아니라 `global/session`에 있고, 거기서 이 도메인의 `AuthErrorCode`를 역참조한다.

## 0. 이 문서를 관통하는 한 줄

**PBKDF2 210,000회가 이 도메인 설계의 거의 모든 것을 결정했다.**

```
PBKDF2 210,000회 (CPU 바운드, 1회 ≈ 150ms)
  → 해싱 중 DB 커넥션을 잡으면 안 됨      → 로그인을 조회/검증 두 메서드로 분리
  → 톰캣 워커가 해싱으로 다 막히면 안 됨   → 동시 해싱을 permits=2 로 묶음
  → 넘치는 요청을 어디에 세울 것인가       → 로그인 대기열(Redis + 힙 하이브리드)
```

아래 2·3·4절은 사실상 이 사슬을 순서대로 푼 것이다.

## 1. API

base: `/api/v1/user`

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/verification-code` | `@PublicApi` | 인증번호 발송 |
| POST | `/verification-code/verify` | `@PublicApi` | 인증번호 검증 |
| POST | `/signup` | `@PublicApi` | 회원가입 |
| POST | `/login` | `@PublicApi` | 로그인 (즉시 성공 또는 대기열 티켓 발급) |
| POST | `/login/queue/{ticketId}` | `@PublicApi` | 대기열 폴링 |
| POST | `/logout` | 세션 필요 | 로그아웃 |
| GET | `/me` | `@LoginUser` | 내 정보 |
| GET | `/role?target=BOORMI\|DREAMI` | `@LoginUser` | 역할 전환 가능 여부 판정 |

- `/login`과 `/login/queue/{ticketId}`는 컨트롤러의 같은 `establishSessionIfReady(...)`로 세션 생성을 공유한다. 순서는 세션 생성 → `ActiveSessionRegistry.replace()` → 이전 SSE 종료(`REPLACED_BY_LOGIN`) → 이전 세션 `invalidate()`.
- 로그아웃은 세션이 없어도 200이다. 로그아웃을 두 번 호출하면 500이 나던 것을 정상 처리로 바꿨다.
- `GET /role`은 이름과 달리 **상태를 바꾸지 않는다**. 판정만 하는 가드다(6절).

## 2. 비밀번호 저장

`service/PasswordHasher.java` — JDK 내장만 쓰고 외부 의존성이 없다.

| 항목 | 값 |
|---|---|
| 알고리즘 | `PBKDF2WithHmacSHA256` |
| salt | 16 bytes, 계정마다 `SecureRandom`으로 새로 생성 |
| 파생 키 | 256 bits |
| 반복 횟수 | **210,000** |
| 저장 포맷 | `"<saltHex>:<hashHex>"` — 97자, `password varchar(255)` 안에 들어간다 |
| 비교 | `MessageDigest.isEqual` (타이밍 세이프) |

`matches`는 `stored == null`, 콜론 분해 실패, hex 파싱 실패를 전부 `false`로 떨어뜨린다. 즉 **형식이 깨진 값(= 평문이 그대로 남아 있는 값)은 자동으로 로그인 불가**가 된다.

평문 저장에서 전환할 때 기존 계정을 마이그레이션하지 않았다. 저장 형식이 깨진 값은 위 규칙에 따라 검증에서 false가 되므로 **기존 계정은 로그인이 불가하고 재가입이 필요하다** — 하위호환 대신 레거시 계정 폐기를 택한 의도적 결정이다.

## 3. 해싱을 트랜잭션 밖으로

`UserService.login`은 한 메서드가 아니라 두 개로 쪼개져 있다.

```java
public UUID login(LoginRequest request) {
    return verify(loadCredential(request.email()), request.password());
}

@Transactional(readOnly = true)
public LoginCredential loadCredential(String email) { ... }   // DB만

public UUID verify(LoginCredential credential, String rawPassword) { ... }  // 해싱만, 트랜잭션 없음
```

이유는 메서드 javadoc에 그대로 적혀 있다. 한 트랜잭션으로 묶으면 `spring.jpa.open-in-view=false` 때문에 **210,000회 해싱이 도는 내내 Hikari 커넥션 하나를 잡고 있어**, 동시 로그인이 풀 크기(10)를 넘는 순간 11번째부터 커넥션 타임아웃 500이 난다.

`LoginCredential`은 `(boormiId, passwordHash, userCd)` 3개만 담은 detached record다. 엔티티를 그대로 들고 나가면 준영속 상태에서 지연로딩 사고가 나므로 필요한 값만 복사한다.

부수 효과 하나: `loadCredential`에서 계정이 없으면 바로 `LOGIN_FAILED`이므로 **존재하지 않는 이메일은 해싱 슬롯을 소모하지 않는다**. 대신 8절의 사이드채널이 생긴다.

계정 상태 판정은 `verify` 안에서 한다 — `RESTRICTED`/`BANNED` → `SUSPENDED_ACCOUNT`(403), `DELETED` → `WITHDRAWN_ACCOUNT`(403). 이메일 없음과 비밀번호 틀림은 **둘 다 `LOGIN_FAILED`(401)**로 같다(사용자 열거 방지).

## 4. 로그인 대기열

구현: `service/LoginQueue.java`, 설정 `LoginQueueProperties`(`@ConfigurationProperties("login.queue")`), Lua 스크립트 3종(`resources/scripts/login-{enqueue,admit,claim}.lua`), 운영 문서 `redis/README.md`.

### 4.1 대기열이 파는 것 / 팔지 않는 것

클래스 javadoc이 먼저 인정한다 — **이 대기열은 처리량을 올리지 못한다.** PBKDF2는 CPU 바운드라 천장이 `vCPU 수 / 해시 시간`으로 고정돼 있다. 파는 것은 셋뿐이다.

1. **격리** — 로그인 폭주가 톰캣 스레드를 다 잡아먹어 나머지 API를 굶기지 않게 한다
2. **예측 가능성** — 사용자가 자기 순번과 예상 대기 시간을 본다
3. **정상 실패** — DB 커넥션 타임아웃 500 대신 명시적 대기 또는 503

### 4.2 하이브리드 저장 — 순번은 Redis, 자격증명은 힙

| 위치 | 구조 | 내용 |
|---|---|---|
| Redis ZSET | `login:queue` | member=ticketId, score=등록 시각(ms). 순번은 `ZRANK+1` |
| Redis HASH | `login:ticket:{ticketId}` | `state`(WAITING/READY/FAILED) + `payload`(boormiId 또는 에러코드) |
| JVM 힙 | `ConcurrentHashMap pending` | ticketId → (email, **평문 password**) |
| JVM 힙 | `LinkedBlockingQueue localQueue` | 해싱 대기 ticketId FIFO |
| JVM 힙 | `Semaphore hashPermits` | 동시 해싱 상한 |

**평문 비밀번호는 Redis에 넣지 않는다.** 근거: Redis는 TLS 미구성이고, 공유 저장소에 자격증명을 두는 것은 힙 대비 명백한 보안 후퇴다. Redis에는 순번·상태·결과만 둔다. 워커가 꺼내는 즉시 `pending`에서 제거하고 어떤 경로로도 로그에 남기지 않는다(관리자 인메모리 프로브도 ticketId만 노출).

`char[]`로 들고 다니며 소거하지는 **않는다** — Jackson이 요청 본문을 이미 불변 String으로 힙에 올려두므로 여기서만 소거해봐야 실효가 없다는 판단이다.

### 4.3 흐름

1. **`submit`** — `hashPermits.tryAcquire()` 성공 **AND** `localQueue.isEmpty()`면 요청 스레드에서 바로 해싱하고 끝낸다. 아니면 `enqueue`해서 `QUEUED` + ticketId를 돌려준다.
   - 이 즉시 처리 경로가 **Redis 장애 대비책을 겸한다**. 대기열이 비어 있으면 Redis를 아예 건드리지 않으므로 Redis가 죽어도 저부하에서는 로그인이 그대로 된다. 고부하에서만 `LOGIN_QUEUE_UNAVAILABLE`(503).
   - 즉시 처리와 워커가 **같은 세마포어**를 쓰므로 두 경로를 합쳐도 permits를 넘지 않는다.
2. **`enqueue`** (`login-enqueue.lua`) — `ZCARD >= capacity`면 만석(`LOGIN_QUEUE_FULL`, 503), 아니면 `ZADD` + `HSET state=WAITING` + `PEXPIRE` + `ZRANK+1`을 원자적으로. **Redis 등록에 성공한 뒤에** 로컬 `pending`/`localQueue`에 넣는다(순서가 뒤집히면 워커가 없는 티켓을 처리하려 든다).
3. **워커** — `@PostConstruct`가 permits 개의 **플랫폼 데몬 스레드**(`login-hasher-N`)를 띄운다. 가상 스레드를 쓰지 않는 이유는 CPU 바운드라 "정확히 permits 개만 동시 실행"이 요점이기 때문. 처리 전 `hasKey(ticketKey)`로 이탈·만료 티켓을 **해싱 비용을 쓰기 전에** 버린다.
4. **`admit`** (`login-admit.lua`) — `ZREM` + 결과 `HSET` + `PEXPIRE readyTtl`을 원자적으로.
5. **`poll`** (`login-claim.lua`) — WAITING이면 `{state, '', ZRANK+1, ZCARD}`, 결과가 있으면 payload를 주고 **`DEL`(1회용 소비)**. 동시 폴링 두 건이 같은 결과를 받아 세션이 두 번 만들어지는 것을 막는다.
6. 컨트롤러는 `boormiId != null`일 때만 세션을 만든다.

로그인 실패·정지·탈퇴는 폴링 시점이 아니라 **결과를 클레임하는 시점**에 터진다.

### 4.4 ETA와 폴링 주기

```
estimatedWaitSeconds = ceil(position × estimated-hash-duration / permits)   (최소 1초)
pollAfterMs          = clamp(estimatedWaitSeconds × 250ms, 500ms, 5000ms)
```

서버가 백오프를 쥐고 있어야 부하 상황에서 조절이 가능하다. 순번이 앞으로 당겨질수록 폴링이 촘촘해지고, 뒤쪽 대기자는 느리게 돈다.

### 4.5 설정값

```properties
login.queue.permits=2                     # 동시 해싱 = vCPU 2
login.queue.capacity=500                  # 초과 시 즉시 거부
login.queue.ticket-ttl=2m
login.queue.ready-ttl=30s
login.queue.estimated-hash-duration=150ms # login.hash 타이머 실측 p50
spring.data.redis.timeout=300ms
management.health.redis.enabled=false     # Redis 장애로 인스턴스가 LB 에서 빠지지 않게
```

### 4.6 정리와 관측

- `sweepExpired()` `@Scheduled(fixedDelay=30s)` — ZSET 멤버에는 개별 TTL이 없어 이탈자가 남아 뒷사람 순번을 부풀린다. `ZREMRANGEBYSCORE`로 훑는다. 스윕으로 멤버만 먼저 사라진 경우 rank가 0으로 오는데 `poll`에서 `Math.max(1, ...)`로 표시를 보정한다.
- 메트릭: `login.hash`(Timer, permits와 estimated-hash-duration의 실측 근거), `login.queue.waiting`(Gauge), `login.queue.enqueued`/`admitted`(Counter), `login.queue.rejected{reason=full|expired|unavailable}`.
- `InMemoryStateProbe` 구현 → `GET /api/v1/admin/inmemory`(`@AdminUser`)로 `localQueue`/`pending` **크기만** 노출.

### 4.7 알려진 한계

- **새치기 가능** — 즉시 처리 경로가 워커를 앞지를 수 있다. 폭은 permits 개이고 best-effort로 허용한다.
- **단일 인스턴스 전제** — 서버를 늘리면 순번(`ZRANK`)은 전역이지만 자격증명이 각 노드 힙에 있어 해싱은 티켓을 받은 노드만 한다. 실제 처리 순서는 노드별 FIFO의 인터리빙이 되고 **순번은 근사값**이 된다.
- **Redis `noeviction` 고정** — `redis/README.md`가 명시. `allkeys-*`/`volatile-*`로 바꾸면 티켓이 evict되어 대기자가 조용히 사라진다. 카카오 캐시가 같은 인스턴스를 쓰므로 OOM이 나면 `enqueue`까지 막혀 로그인이 503이 된다.

### 4.8 프론트 연동

`frontend/src/shared/store/sessionStore.ts`, `pages/login/ui/LoginQueueModal.tsx`, `pages/login/ui/LoginScreen.tsx`.

- 티켓은 **`sessionStorage`** 키 `naengsam.loginTicket`에 저장한다. 새로고침해도 줄을 잃지 않되 **탭을 닫으면 버린다**(닫은 탭의 줄을 되살릴 이유가 없다).
- 폴링 간격은 서버가 준 `pollAfterMs`를 그대로 쓴다(없으면 1000ms).
- 스토어 초기값이 저장된 티켓을 읽으므로 **새로고침 첫 렌더부터 모달이 유지된다**(로그인 폼 번쩍임 방지).
- 진행률 분모는 **최초 순번**으로 고정한다 — 바가 되감기지 않게.
- 모달에 `onClose`를 넘기지 않아 **닫히지 않는다**. 대기 중에 폼으로 돌아가 재제출하면 티켓만 늘고 순번이 뒤로 밀리기 때문이다.
- 모듈 스코프 `resuming` 플래그로 StrictMode 이중 effect를 막는다(같은 티켓으로 두 루프가 돌면 하나가 결과를 소비하고 다른 하나는 410).
- 로그인 응답에는 사용자 정보가 없으므로(쿠키만) 성공 후 항상 `GET /me`를 다시 부른다.

## 5. 세션과 인가 (`global/session`)

세션 저장소는 **Redis가 아니라 톰캣 인메모리 `HttpSession`**이다. Redis는 로그인 대기열 전용이고, Spring Session 의존성은 없다.

| 요소 | 역할 |
|---|---|
| `LoginSession` | `HttpSession` 래퍼. `create()`가 기존 세션을 `invalidate()`한 뒤 새로 발급 — **세션 고정 공격 방어**. 세션 속성 키는 `SessionConst.LOGIN_USER` 하나 |
| `ActiveSessionRegistry` | **사용자당 활성 세션 1개 + 세션당 SSE 연결 1개**의 단일 진실 공급원. `sessionsByUser` + `usersBySessionId` 역인덱스 |
| `LoginCheckInterceptor` | `/api/**` 전부 기본 보호. 세션 attribute 존재 **AND** `registry.isCurrent(userId, sessionId)` 둘 다여야 통과 → 다른 기기에서 재로그인하면 이전 세션은 만료 전이라도 401 |
| `LoginUserArgumentResolver` | `@LoginUser UUID` 주입, 없으면 `UNAUTHORIZED` |
| `AdminUserArgumentResolver` | `@AdminUser UUID` 주입. `Boormi.isAdmin()` 확인, 아니면 `FORBIDDEN_ROLE`. **요청마다 BOORMI 조회 1회** |
| `SessionExpirationListener` | 톰캣 타임아웃 시 `removeIfCurrent`로 **여전히 현재 세션일 때만** SSE 종료 — 늦게 도착한 이전 세션 만료 콜백이 새 세션 SSE를 끊지 않게 |
| `@PublicApi` | 인증 opt-out 마커 |

인증은 **opt-in이 아니라 opt-out**이다. 원래는 `@LoginRequired`를 붙이는 방식이었는데, 붙이는 걸 잊으면 그대로 열려버린다. `@PublicApi`로 뒤집어서 **실수하면 닫히는 방향**으로 만들었다. 현재 `@PublicApi` 사용처는 UserController 5개 + 배달 테스트 콘솔 + 푸시 구독 컨트롤러뿐이다.

쿠키는 `SameSite=None; Secure`(크로스오리진 전송), CORS는 `allowCredentials(true)` + `cors.allowed-origins` 화이트리스트.

**세션 ID는 로그에 남기지 않는다.** `ActiveSessionRegistry.replace`도 `이전 세션 존재={}` boolean만 찍는다. 이건 테스트로 강제돼 있다(logback `ListAppender`로 로그를 캡처해 세션 ID 미기록을 단언).

## 6. 역할 전환 가드

`UserService.changeRole(boormiId, target)` — **상태를 바꾸지 않는다.** 현재 모드는 클라이언트가 sessionStorage로 보관하고, 서버는 "전환해도 되는가"만 판정한다.

1. `target == DREAMI`일 때만 드리미 등록·승인을 검사한다(미등록 `DREAMI_NOT_REGISTERED`, 미승인 `DREAMI_NOT_APPROVED`). 부르미는 모든 계정의 기본 역할이라 검사가 없다.
2. `UserActivityResolver.resolve(boormiId)`로 지금 수행 중인 역할을 판정한다. 비활성이거나 요청한 역할과 같으면 통과(무변화).
3. 그 외는 차단하되 안내를 나눈다 — `orderId == null`이면 `CANNOT_CHANGE_ROLE_WHILE_MATCHING`(드리미가 오퍼를 기다리는 중, 오프라인으로 사용자가 직접 풀 수 있다), `orderId != null`이면 `CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER`.

`UserActivityResolver`가 이 도메인의 두 번째 축이다.

- 활성 주문 상태 집합: `MATCHING`, `PENDING_BOORMI_CONFIRMATION`, `IN_PROGRESS`, `WAITING_CONFIRMATION`
- **드리미를 먼저 조회한다** — `PENDING_BOORMI_CONFIRMATION`은 `boormi_id`/`dreami_id` 어느 쪽으로도 잡히므로, 순서를 뒤집으면 드리미를 부르미로 오판한다.
- 주문이 없어도 `matchingService.isDreamiWaiting(userId)`면 활성으로 본다. **DB에 흔적이 없는 유일한 활성 상태**(매칭 엔진 인메모리)다.
- 재사용처: `UserService.getUserInfo`(로그인 후 화면 복귀), `changeRole`, 그리고 도메인 밖 `DreamiService`의 goOnline 가드.

원래 `GET /user/role`에는 방향 파라미터가 없었고 프론트가 부르미 방향으로는 호출조차 하지 않아서, **드리미 → 부르미 전환은 배달 중에도 통과했다**. `target`을 필수로 만들어 양방향을 검사하도록 고쳤다. 같은 변경에서 `getUserInfo`도 이 리졸버를 쓰게 바꿨는데, 그 전 구현은 `IN_PROGRESS`만 드리미로 보고 `PENDING_BOORMI_CONFIRMATION`은 활성 주문 카운트에 걸려 부르미로 오판하고 있었다.

## 7. 문자인증

`SmsVerificationService` + `VerificationCodeStore` + `SmsSendRateLimiter`. 저장소는 전부 인메모리(단일 인스턴스 전제, best-effort).

```properties
verification.code-ttl=5m
verification.resend-cooldown=60s
verification.verified-ttl=30m
verification.max-verify-attempts=5
verification.phone-window=24h    verification.phone-max=5
verification.global-window=24h   verification.global-max=1000
```

정책:

- **brute-force 차단** — 시도 5회를 넘기면 코드를 폐기한다. 이후에는 **정답을 넣어도 `EXPIRED`**다. 재발급하면 시도 횟수가 초기화된다. 카운트 증가와 폐기는 `compute`로 원자화했다.
- **발송 남용 방지** — 번호별/전역 rate limit(문자폭탄·과금 공격 대비). **어느 한도에 걸렸는지는 응답에 노출하지 않고 429로 통일**한다.
- **검증 멱등** — 이미 검증된 번호를 다시 검증하면 `ALREADY_VERIFIED`. 원래는 "만료"로 나와서 사용자가 혼란스러웠다.
- 가입은 인증된 번호가 아니면 `PHONE_NOT_VERIFIED`이고 **저장 자체를 하지 않는다**. 성공 시 정규화된 번호로 저장 + 지갑 생성 + 인증 상태 소비가 한 트랜잭션이다.
- 인메모리 현황 프로브가 휴대폰번호·인증번호를 노출하지 않는 것도 테스트로 고정돼 있다.

SMS 발송은 `SmsSender` 인터페이스 뒤에 `DevSmsSender`/`SolapiSmsSender` 두 구현이 있다(`solapi.enabled`).

## 8. 남은 리스크

1. **응답 시간 사이드채널** — 존재하지 않는 이메일은 해싱을 건너뛰므로 응답이 눈에 띄게 빠르다. 응답 코드는 `LOGIN_FAILED`로 같지만 시간으로 계정 존재 여부를 구분할 수 있다. 3절의 "계정 없으면 해싱 슬롯을 안 쓴다"는 이점과 맞바꾼 지점이다.
2. **다중 인스턴스에서 순번은 근사값** (4.7).
3. **인메모리 상태 전반이 단일 인스턴스 전제** — `VerificationCodeStore`, `SmsSendRateLimiter`, `ActiveSessionRegistry`, 매칭 엔진이 모두 같은 전제 위에 있다. `InMemoryStateProbe`로 그 한계를 관측하는 것으로 보완하고 있다.
4. **`AdminUserArgumentResolver`가 요청마다 BOORMI를 조회한다** — 관리자 트래픽이 늘면 캐시가 필요할 수 있다.

## 9. 다른 도메인과의 연결

- **[Boormi 도메인](../boormi/overview.md)** — 계정 엔티티 자체가 `BOORMI`다. `isDreamiActivate`/`isAdmin` 플래그의 의미는 그쪽 문서 참고.
- **[Payment 도메인](../payment/overview.md)** — `signup`이 `WalletService.createWallet`을 같은 트랜잭션에서 호출한다(지갑 3행 동시 생성).
- **[Dreami 도메인](../../hyeonseo/dreami/overview.md)** — 드리미 승인 여부(`requestCd`)를 역할 전환 가드가 읽는다.
