# `global` 패키지 정리

`backend/src/main/java/com/naengsam/quick/global/` 아래를 훑어본 정리 노트. `backend/CLAUDE.md`는
`commonResponse · code · exception · session · swagger · config` 6개만 언급하지만, 실제 코드에는
`sse · notification · admin` 3개가 더 있다(모두 특정 도메인에 속하지 않는 횡단 인프라라서 `global`에 있음).

패키지 전체 공통 설계 원칙: **도메인(domain/*)은 서로 독립적으로 유지하고, 여러 도메인이 공유해야 하는 것만
`global`에 둔다.** 그래서 `global` 코드가 `domain` 코드를 참조하는 것(예: `AdminUserArgumentResolver`가
`Boormi` 엔티티를 참조)은 있어도, 반대로 도메인이 도메인을 직접 참조하지 않도록 짜여 있다.

---

## 1. `code` — 응답 코드의 타입 계층

가장 기초가 되는 패키지. 인터페이스 2개 + enum 2개뿐이다.

```
BaseCode (interface)          — status, code, message
  └─ BaseErrorCode (interface) — 마커 역할, 필드 추가 없음
```

- `BaseCode`: 성공/실패 가리지 않고 "HTTP 상태 + 코드 문자열 + 메시지"를 갖는 모든 것의 계약.
- `BaseErrorCode`: `BaseCode`를 상속만 할 뿐 아무 것도 더하지 않는 **마커 인터페이스**. 굳이 나눈 이유는
  "이 자리엔 에러 코드만 와야 한다"는 걸 타입 시스템으로 강제하기 위함 — `BusinessException`의 생성자나
  `CommonResponse.onFail(...)`은 `BaseErrorCode`만 받는다.
- `GeneralSuccessCode` / `GeneralErrorCode`: 도메인에 속하지 않는 공통 코드(`COM200`, `COMMON_001` 등)를
  담은 enum. 각 도메인은 `AuthErrorCode`, `UserErrorCode`처럼 **자기 도메인 전용 enum**을 만들어
  `BaseErrorCode`를 구현한다.

**왜 인터페이스로 뺐는가**: enum은 다중 상속(클래스 상속)이 안 되지만 인터페이스는 구현할 수 있다. 도메인마다
에러 코드 enum이 따로 있어야 도메인 독립성이 지켜지는데(백엔드 CLAUDE.md 원칙), 그러면서도
`GlobalExceptionHandler`나 `CommonResponse`처럼 도메인을 모르는 공통 코드가 "코드 하나"로 다룰 수 있어야
한다. 인터페이스 기반 다형성이 이 두 요구를 동시에 만족시키는 방법이다 — 호출부는 `BaseErrorCode` 타입 하나만
알면 되고, 실제로 어떤 도메인의 어떤 enum 상수인지는 몰라도 된다.

---

## 2. `commonResponse` — 응답 봉투

```java
public class CommonResponse<T> {
    Boolean isSuccess; String code; String message; T result;
    static <T> CommonResponse<T> onSuccess(BaseCode code, T result)
    static <T> CommonResponse<T> onSuccess(T result)              // GeneralSuccessCode.OK 기본값
    static <T> CommonResponse<T> onFail(BaseErrorCode code, T result)
}
```

제네릭 `<T>`로 `result`의 타입을 유지한 채, `isSuccess/code/message`는 항상 같은 모양으로 나가게 한다.
`@JsonPropertyOrder`로 JSON 키 순서까지 고정해 프론트가 보는 응답 모양이 항상 동일하게 유지된다.

**`CommonResponseAdvice`** (`ResponseBodyAdvice<Object>` 구현체)가 이 봉투를 자동으로 씌운다.

- Spring MVC가 컨트롤러 리턴값을 HTTP 응답 바디로 직렬화하기 **직전**에 가로채는 확장점이 `ResponseBodyAdvice`다.
  이 인터페이스를 구현한 `@RestControllerAdvice` 빈을 등록해두면, 대상 패키지의 모든 컨트롤러 리턴값이
  이곳을 거친다. 그 덕에 **컨트롤러는 순수 DTO나 `void`만 반환**하고, 봉투 씌우기는 한 곳에 모을 수 있다
  (`backend/CLAUDE.md`의 "컨트롤러는 DTO를 그대로 반환" 규칙이 여기서 강제된다).
- `basePackages`를 `domain`, `global.notification`, `global.admin`으로 **명시적으로 한정**해야 하는 이유가
  주석에 남아 있다: 범위를 안 좁히면 springdoc의 `OpenApiWebMvcResource`도 `@RestController`라서
  `/v3/api-docs` 응답까지 봉투로 감싸버려 Swagger UI가 깨진다. `sse`는 스트림 응답이라 애초에 제외.
- `body`가 `CommonResponse`(이미 감쌈), `byte[]`, `Resource`(파일 응답)면 그대로 통과시킨다. `String`은
  `StringHttpMessageConverter`가 처리하기 때문에 객체를 리턴하면 캐스팅 예외가 나서, 직접
  `objectMapper.writeValueAsString(...)`으로 JSON 문자열을 만들어 리턴한다 — Jackson 3
  (`tools.jackson.databind.ObjectMapper`)을 쓰는 것도 눈에 띄는 부분(스프링 부트 4 기본 탑재 버전).
- `body`가 `null`(리턴 타입이 `void`)이어도 빈 바디 대신 `result: null`이 담긴 봉투를 내려보낸다 — 응답
  모양의 일관성이 "값이 있을 때만" 지켜지는 게 아니라 항상 지켜지게 하려는 것.

---

## 3. `exception` — 예외 → HTTP 응답 변환

```java
public class BusinessException extends RuntimeException {
    private final BaseErrorCode errorCode;
}
```

도메인 서비스는 `throw new BusinessException(AuthErrorCode.UNAUTHORIZED)`처럼 **에러 코드 enum 하나만
던지면 끝**이다. try-catch로 응답을 조립하거나 `ResponseEntity`를 직접 만들지 않는다
(`backend/CLAUDE.md` 규칙).

**`GlobalExceptionHandler`**(`ResponseEntityExceptionHandler` 상속 + `@RestControllerAdvice`)가 모든
예외를 잡아 `CommonResponse.onFail(...)`로 변환한다. 눈여겨볼 설계:

- `ResponseEntityExceptionHandler`를 상속한 이유: Spring MVC가 파라미터 바인딩 실패, 지원하지 않는
  Content-Type 등에서 내부적으로 던지는 표준 예외들(`MethodArgumentNotValidException`,
  `HttpMessageNotReadableException` 등)을 처리하는 템플릿 메서드 `handleExceptionInternal(...)`을
  오버라이드할 수 있게 해준다. 상태 코드는 Spring이 정한 그대로 두고, **바디만** 공통 포맷으로 바꿔치기한다.
- `resolve(Exception, HttpStatusCode)`에서 Java 21의 **패턴 매칭 switch**(`case Xxx ignored -> ...`)로
  예외 타입 → `GeneralErrorCode`를 매핑한다. 못 찾으면 HTTP 상태 코드 기준으로 한 번 더 폴백한다.
- 로그 레벨을 분리한 것도 의도적이다: `errorCode.getStatus().is5xxServerError()`면 서버 결함이므로
  스택트레이스 전체를 `log.error`로, 4xx는 "의도된 흐름"(비밀번호 오류 등 정상적인 실패)이므로 `log.warn`
  한 줄 + 디버그 레벨에만 스택트레이스. `origin(Throwable)`은 스택트레이스 전체 대신 **던진 지점 한 줄만**
  뽑아 로그를 가볍게 유지한다.
- `OptimisticLockingFailureException`과 `DataIntegrityViolationException`을 따로 잡아 `COMMON_008`
  (409 Conflict)로 응답한다. 둘 다 "서비스 로직에서 사전 검사는 통과했지만 DB 레벨에서 동시성 경합이
  일어난" 드문 케이스라, 500으로 감추지 않고 프론트가 "새로고침 후 재시도" 안내를 할 수 있게
  409로 구분해준다.
- 마지막 캐치올(`Exception.class`)만 500으로 떨어진다.

---

## 4. `session` — 로그인 인증/인가

### 로그인이 "어떻게" 되어 있는가

이 서비스는 JWT 같은 토큰 인증이 아니라 **서블릿 컨테이너(톰캣)의 `HttpSession` + 쿠키(JSESSIONID)** 를
그대로 쓴다. `LoginSession`이 그 위에 얇게 씌운 타입 안전 래퍼다.

```java
public class LoginSession {
    static LoginSession create(HttpServletRequest)     // 로그인 시 — 기존 세션 있으면 invalidate 후 새로 발급
    static Optional<LoginSession> current(HttpServletRequest) // 조회 — 없으면 empty
    void login(UUID boormiId)                          // 세션에 사용자 ID 저장
    Optional<UUID> boormiId()
    boolean isLoggedIn()
    void invalidate()                                  // 로그아웃
}
```

- `create()`가 로그인할 때마다 기존 세션을 무효화하고 새 세션을 발급하는 이유는 **세션 고정(Session
  Fixation) 공격 방지** — 로그인 전에 발급된 세션 ID가 로그인 후에도 그대로 유효하면 공격자가 미리 심어둔
  세션 ID로 피해자를 로그인시켜 탈취할 수 있다.
- 값 저장은 `session.setAttribute(SessionConst.LOGIN_USER, boormiId)` 하나뿐. `SessionConst`는 이 문자열
  키 하나만 들고 있는 상수 클래스(private 생성자로 인스턴스화 방지).

### "로그인 필요/공개 API" 판단

- `@interface PublicApi` — 메서드/클래스에 붙이면 로그인 없이 접근 가능(로그인, 회원가입, 인증문자 등).
- `LoginCheckInterceptor`(`HandlerInterceptor` 구현) — `/api/**`에 걸리는 인터셉터. 핸들러에
  `@PublicApi`가 없으면 로그인을 요구한다. **`HandlerInterceptor`는 Spring MVC가 컨트롤러 메서드를
  호출하기 전에 가로채는 확장점**으로, `preHandle`이 `false`를 반환하거나 예외를 던지면 컨트롤러까지
  요청이 도달하지 않는다 — 그래서 인증 검사를 컨트롤러마다 반복하지 않고 한 곳에 모을 수 있다.
- 여기서 중요한 디테일: 세션에 `LOGIN_USER` attribute가 있다는 것만으로는 통과시키지 않는다.
  `ActiveSessionRegistry.isCurrent(userId, sessionId)`로 **이 servlet 세션이 지금도 그 계정의
  "현재" 활성 세션인지**까지 확인한다(아래 참고). 즉 로그인 attribute가 살아 있어도 다른 곳에서 재로그인해
  ActiveSession이 교체됐다면 더 이상 유효하지 않다.

### `@LoginUser` / `@AdminUser` — 커스텀 파라미터 주입

```java
@GetMapping("/me")
public MeResponse me(@LoginUser UUID boormiId) { ... }
```

두 어노테이션 다 `HandlerMethodArgumentResolver`를 커스텀 구현해서 동작한다. 이 인터페이스는 Spring MVC가
컨트롤러 메서드의 파라미터를 채울 때 쓰는 확장점으로, `@RequestParam`, `@PathVariable` 같은 표준 애노테이션도
내부적으로 이 방식으로 구현되어 있다. `WebConfig.addArgumentResolvers(...)`에 등록해두면 프레임워크가
파라미터마다 `supportsParameter()`를 물어보고, 맞으면 `resolveArgument()`의 리턴값을 그대로 주입한다.

- `LoginUserArgumentResolver`: `@LoginUser UUID` 파라미터를 발견하면 `LoginSession.current(request)`에서
  `boormiId()`를 꺼내 주입. 없으면 `AuthErrorCode.UNAUTHORIZED`.
- `AdminUserArgumentResolver`: 로그인 여부까지는 같지만, 추가로 `BoormiRepository`에서 엔티티를 조회해
  `boormi.isAdmin()`을 확인한다. 로그인은 했지만 관리자가 아니면 `AuthErrorCode.FORBIDDEN_ROLE`.

**"드리미인지 부르미인지" 판단은 여기 없다** — 관리자 판별(`isAdmin()`)만 `global`이 다루고, 실제
"이 계정이 지금 부르미 모드인지 드리미 모드인지"(역할 스와이프) 같은 도메인 개념은
`domain/user`, `domain/boormi`, `domain/dreami` 쪽 책임이다. `global.session`은 오직 "누구로 로그인했나"와
"관리자인가"까지만 본다.

### `ActiveSession` / `ActiveSessionRegistry` — 계정당 세션 1개 정책

이 서비스는 **한 계정이 동시에 여러 곳에서 로그인해 있는 것을 허용하지 않는다**(멀티탭/재로그인 정책).
그 단일 진실 공급원이 `ActiveSessionRegistry`다.

```java
record ActiveSession(String sessionId, LoginSession session, SseConnection sseConnection)
```

- 내부 자료구조는 두 개의 `ConcurrentHashMap`: `sessionsByUser`(userId → 활성 세션)와
  `usersBySessionId`(sessionId → userId, 역인덱스). 서블릿 세션 저장소를 직접 순회할 수 없으니, "이
  세션이 최신 세션인가"를 빠르게 확인하려고 애플리케이션 레벨에 별도 인덱스를 둔 것이다.
- `replace(userId, newSession)`가 로그인 때 호출된다. 새 `ActiveSession`으로 갈아끼우고 **이전 세션을
  반환**하므로, 호출자(로그인 서비스)가 그 이전 세션의 서블릿 세션을 무효화하고 SSE 연결을 끊는 뒷정리를
  할 수 있다. 즉 "나중에 로그인한 사람이 이긴다" 정책이 여기서 구현된다.
- 동시성 관련 메서드가 전부 `compute`/`computeIfPresent`의 **원자적 갱신**을 쓴다는 점이 특징이다.
  예를 들어 `replaceSseIfCurrent`의 주석에는 "`SseEmitter.complete()`가 즉시 completion 콜백을 실행할
  수 있고, 그 콜백이 같은 userId로 registry를 다시 건드리면 `compute` 재진입 문제가 생긴다"는 이유로,
  실제 emitter 종료(`close(...)`)는 **`compute` 블록 밖에서** 호출하도록 설계돼 있다. `ConcurrentHashMap`의
  computeIfPresent 콜백 안에서 같은 맵을 다시 건드리면 데드락/`ConcurrentModificationException` 계열
  문제가 날 수 있다는 걸 회피한 것.
- `snapshotSseConnections()`는 heartbeat가 순회하는 동안 registry가 잠기지 않도록 **불변 리스트로 복사해서
  넘긴다** — 원본 맵을 그대로 노출하면 순회 중 다른 스레드의 갱신과 충돌할 여지가 있다.
- `InMemoryStateProbe`를 구현해 관리자 진단 API(`global.admin`)에도 자기 상태를 보고한다.

### `SessionExpirationListener` — 세션 타임아웃 정리

`HttpSessionListener`(서블릿 표준 인터페이스)를 구현해 톰캣이 세션을 타임아웃으로 무효화할 때
`sessionDestroyed` 콜백을 받는다. 이 세션이 **여전히** 활성 세션일 때만(`removeIfCurrent`) SSE 연결을
정리한다 — 이미 재로그인으로 교체된 이전 세션의 "뒤늦은 만료 이벤트"가 새 세션의 연결을 잘못 끊지 않게
하기 위해서다.

---

## 5. `config` — 전역 설정 빈

- **`ClockConfig`**: `Clock.systemDefaultZone()`을 빈으로 등록. 서비스 코드가 `LocalDateTime.now()`를
  직접 부르지 않고 주입받은 `Clock`을 쓰게 하면, 테스트에서 `Clock.fixed(...)`로 시각을 고정해 "지금
  몇 시인지"에 의존하는 로직(만료 판정 등)을 결정적으로 테스트할 수 있다. `InMemoryStateController`도
  스냅샷 시각을 이 `Clock`으로 찍는다.
- **`RedisConfig`**: 로그인 대기열이 쓰는 Lua 스크립트 3개(`login-enqueue`, `login-admit`, `login-claim`)를
  `DefaultRedisScript` 빈으로 등록. Lua 스크립트로 묶는 이유는 **원자성**이다 — "용량 확인 후 등록",
  "결과 기록 후 대기열 제거", "결과 조회 후 티켓 소비"를 각각 별도 Redis 왕복으로 나누면 동시 요청에서
  경쟁 상태(정원 초과 미검출, 순번만 줄고 결과 없음, 세션 중복 생성)가 생기기 때문에, 서버에서 한 번에
  원자적으로 실행되는 Lua 스크립트로 처리한다. 연결·직렬화는 Spring Boot가 자동 구성하는
  `StringRedisTemplate`을 그대로 쓴다.
- **`WebConfig`**(`WebMvcConfigurer` 구현): 인터셉터(`LoginCheckInterceptor`)와
  ArgumentResolver 2개를 등록하고, `/api/**`에 대한 CORS 정책(`allowedOrigins`는
  `cors.allowed-origins` 환경변수 주입, `allowCredentials(true)`로 세션 쿠키 전송 허용)을 정의한다.
  `@EnableWebMvc`는 일부러 안 붙인다 — 붙이면 Spring Boot의 자동 설정(정적 리소스 매핑, 메시지 컨버터
  기본값 등)이 꺼지기 때문에, `WebMvcConfigurer` 인터페이스만 구현해 필요한 부분만 얹는 방식을 쓴다.

---

## 6. `swagger` — API 문서 자동화

컨트롤러는 DTO만 반환하지만 실제 응답은 `CommonResponseAdvice`가 봉투로 감싸므로, 그대로 두면 Swagger
문서가 실제 응답과 어긋난다. 이 어긋남을 메꾸는 커스터마이저들이 전부 springdoc의 `OperationCustomizer`
확장점을 구현한다(각 API operation의 OpenAPI 스펙 객체를 후처리하는 훅).

- **`SwaggerConfig`**: `OpenAPI` 빈(서버 목록, 세션 쿠키 시큐리티 스킴 정의)을 등록. static 블록에서
  `SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class)`를 호출하는데, 이건 `@LoginUser`가
  붙은 파라미터를 springdoc이 "클라이언트가 보내는 쿼리 파라미터"로 착각해 문서화하지 않도록 전역 제외
  처리한 것 — 실제로는 서버가 세션에서 채워 넣는 값이라 클라이언트 입력이 아니기 때문이다. static 블록인
  이유는 설정 빈이 인스턴스화되는 시점(컨텍스트 로딩 중)에 실행돼야 이후의 springdoc 스캔에 반영되기
  때문.
- **`CommonResponseSchemaCustomizer`**: 2xx 응답 스키마를 `{isSuccess, code, message, result}` 모양으로
  다시 감싼다. 컨트롤러 리턴 타입이 이미 `CommonResponse`면 건드리지 않는다.
- **`ApiErrorCodes`** (`@Repeatable` 커스텀 애노테이션) + **`ApiErrorCodeCustomizer`**: 컨트롤러 메서드에
  `@ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})`처럼 붙이면, 커스터마이저가
  enum 상수에서 상태 코드/코드/메시지를 **직접 읽어** Swagger 응답 예시로 채워 넣는다. 문서에 에러 메시지를
  손으로 옮겨 적지 않으므로 실제 코드와 문서가 어긋날 일이 없다. 여러 도메인의 에러 코드를 한 API에
  노출하려면 애노테이션을 여러 번 붙이면 되도록 `@Repeatable` 컨테이너(`ApiErrorCodesContainer`)를 함께
  정의해뒀다.
  - 내부적으로 예시 값을 만들 때 실제 `CommonResponse.onFail(...)`을 Jackson으로 직렬화한 뒤 그 결과를
    다시 `JsonNode`로 파싱해서 넣는다. 문자열로 넣으면 Swagger UI가 이스케이프된 채로 보여주고, 자바
    객체로 넣으면 swagger의 NON_NULL 설정 때문에 `result: null`이 사라지는 문제가 있어서, 그 중간 지점인
    `JsonNode`로 우회한 것.
- **`SessionSecurityCustomizer`**: `@PublicApi`가 없는 오퍼레이션에 세션 시큐리티 요구사항을 걸어
  Swagger UI에 자물쇠 아이콘을 표시한다. 전역 시큐리티가 아니라 오퍼레이션 단위로 개별 부여한다.

---

## 7. `sse` — 실시간 이벤트 스트림

계정당 SSE(Server-Sent Events) 연결을 **최대 1개**만 허용하는 정책으로 짜여 있다. 매칭 오퍼, 배달 위치
갱신 같은 실시간 이벤트를 이 연결로 흘려보낸다.

```
SseController → SseService → SseConnectionManager → ActiveSessionRegistry (연결 소유권의 단일 진실 공급원)
```

- `SseConnection(connectionId, SseEmitter)` — record. `connectionId`는 재구독 시 옛 연결을 새 연결로
  교체할 때, **늦게 도착한 이전 연결의 콜백이 이미 교체된 새 연결을 잘못 건드리지 않도록** 신원을 구분하는
  용도다(session/registry 쪽 코드 전반에서 반복되는 "identity 비교 후에만 제거" 패턴).
- `SseEventType` — 인터페이스 하나(`eventName()`). 각 도메인이 자신의 이벤트를 이 인터페이스를 구현하는
  enum으로 정의해 사용한다. `NotificationPolicy`와 연동되는 확장점이다.
- **`SseConnectionManager`**: 연결 수립(`connect`)/해제(`disconnect`)/전송(`send`)/heartbeat 담당.
  - `connect()`: sessionId가 더 이상 현재 세션이 아니면(인터셉터 통과 직후의 경합) 즉시 종료하고 401.
    아니면 기존 연결이 있어도 거부하지 않고 **즉시 교체**하며, 옛 emitter 종료는 registry 갱신이 끝난
    "뒤에" 한다(마찬가지로 completion 콜백 재진입 회피).
  - `send()`: 전송 시작 시점에 연결을 스냅샷으로 떠서, 전송 도중 다른 탭이 연결을 교체해도 이 전송은
    스냅샷 시점 연결로 끝까지 진행된다. 실패 시엔 "그 스냅샷이 지금도 현재 연결일 때만" registry에서
    제거한다.
  - `sendHeartbeats()`: `@Scheduled(fixedRateString = "${sse.heartbeat-interval}")`로 주기 실행. 프록시나
    브라우저가 말없이 끊은 유령 연결을 정리하는 최종 회수 수단. 데이터 없는 SSE 주석(`:heartbeat`)이라
    클라이언트 `EventSource`에는 노출되지 않는다.
  - Micrometer `Gauge`/`Counter`(`sse.connections.active`, `sse.events.sent`, `sse.connections.closed` 등)로
    연결 수·이벤트량을 Grafana에서 관측할 수 있게 노출한다.
- **`SseService`**: 실제 전송을 **단일 가상 스레드**(`Executors.newSingleThreadExecutor(Thread.ofVirtual()...)`)로
  오프로딩한다. 이렇게 하는 이유 두 가지 — (1) 호출 스레드(매칭 엔진의 단일 writer 스레드 등)가 느리거나
  죽은 클라이언트 때문에 막히지 않게 하고, (2) 전송 스레드가 하나뿐이라 **이벤트 발생 순서 = 전송 순서**가
  보장된다. `@PreDestroy`로 애플리케이션 종료 시 executor를 정리.
- **`SseController`**: `/api/v1/sse/subscribe`(GET, `text/event-stream`), `/api/v1/sse/disconnect`(POST).
  `disconnect`는 탭이 닫힐 때 `sendBeacon`으로 호출되는 걸 전제로 한다.
- **`SseProperties`**: `@ConfigurationProperties(prefix = "sse")`를 붙인 **record**로
  `heartbeatInterval`/`connectionTimeout`을 바인딩. record를 설정 프로퍼티 홀더로 쓰는 건 Spring Boot 3+에서
  지원하는 방식으로,불변 값 객체를 보일러플레이트 없이 만들 수 있다.

---

## 8. `notification` — 알림 파사드 (인앱 + 웹푸시 + SMS)

도메인이 사용자에게 알림을 보낼 때 **직접 SSE나 웹푸시를 부르지 않고 이 파사드 하나만** 거치게 만든 계층.

```
도메인 서비스 → NotificationService.notify(userId, eventType, payload)
                    ├─ NotificationPolicy.planFor(eventType) → ChannelPlan(어떤 채널로 보낼지 + 웹푸시 문구)
                    ├─ IN_APP 이면 → SseService.send(...)
                    └─ WEB_PUSH 면 → outbound executor에 위임 → WebPushSender → 외부 푸시 서비스
(SMS는 이 파사드 밖: SmsFallbackNotifier를 도메인이 직접, 아주 제한된 상황에서만 호출)
```

- **`NotificationChannel`**: `IN_APP`, `WEB_PUSH` 두 값뿐인 enum.
- **`ChannelPlan`**(record): 이벤트 하나를 "어떤 채널 집합 + 웹푸시 제목/본문/TTL"로 보낼지 담은 불변 계획.
  `Set<NotificationChannel> channels`를 컴팩트 생성자에서 `Set.copyOf(...)`로 방어적 복사한다. 정적
  팩토리 `inAppOnly()` / `inAppAndWebPush(title, body, ttl)`로만 만들고, `withPushSubject(subject)`로
  "물품명 등을 제목 앞에 붙인 새 계획"을 파생시킨다(원본은 불변, 파생은 새 인스턴스). 다만 배달 확정 전
  매칭 단계 알림에는 이 subject를 절대 붙이면 안 된다는 주석이 있다 — 아직 수락하지 않은 사람의 잠금화면에
  남의 주문 정보(물품명)가 노출되는 걸 막기 위해서다.
- **`NotificationPolicy`**: SSE 이벤트 이름(`String`) → `ChannelPlan`의 **결정표**를 `Map.ofEntries(...)`로
  하드코딩해 갖고 있다. 등록되지 않은 이벤트는 기본적으로 `inAppOnly()`로 처리 — "새 이벤트를 실수로 만들면
  외부 채널(웹푸시/SMS)로 안 나가고 안전한 인앱으로만 간다"는 fail-safe 기본값. 각 항목에 붙은 주석이 실제
  설계 판단(TTL 값, Urgency 임계치, 왜 이 이벤트는 웹푸시를 타면 안 되는지 등)을 촘촘히 설명한다.
- **`NotificationService`**: 이 패키지의 진입점.
  - **스레드 제약이 핵심 설계 포인트**다: `notify(...)`가 매칭 엔진의 **단일 writer 스레드**에서도 호출될 수
    있으므로, 이 클래스에서 블로킹 I/O를 호출 스레드에서 동기 실행하면 절대 안 된다. IN_APP은 기존
    `SseService`의 단일 sender 스레드로 위임(순서 보존 유지), WEB_PUSH는 별도 `ThreadPoolExecutor`
    (`outbound`, 가상 스레드 4개, 유계 큐 1,000)로 오프로드한다.
  - 큐를 **유계**로 둔 이유: 외부 푸시 서비스가 죽으면 태스크가 HTTP 타임아웃만큼씩 밀려 쌓이는데, 무한
    큐는 JVM 메모리를 잠식한다. 넘치면 그냥 버린다 — 어차피 이 알림들은 TTL이 짧아 늦게 가는 wake-up은
    의미가 없다는 판단. `ObjectProvider<WebPushSender>`로 주입받는 이유는 `web-push.enabled=false`일 때
    `WebPushSender` 빈이 아예 등록되지 않기 때문에, 일반 `@Autowired`로는 기동이 실패한다 — `ObjectProvider`는
    빈이 없어도 `getIfAvailable()`이 `null`을 돌려주는 지연/선택적 주입을 지원한다.
  - `isReachableNow(userId)`: "푸시 구독이 있는지"가 아니라 **"지금 SSE로 연결돼 있는지"**를 기준으로 삼는다.
    30초짜리 오퍼에 푸시로 깨워서 응답하는 건 현실적으로 불가능하므로, 오퍼 후보 선정에는 실시간 연결
    여부만 의미 있다는 판단.
- **`PushSubscription`**(엔티티): 브라우저 Web Push 구독 1건. **`endpoint` 자체를 기기 식별자로 쓴다** —
  별도 디바이스 테이블이나 클라이언트가 만드는 UUID 없이, "브라우저+기기+서비스워커 등록 조합마다
  endpoint가 유일하다"는 Web Push 표준 성질을 그대로 활용한다. unique 키가 `(boormi_id, endpoint)`가
  아니라 `endpoint` 단독인 것이 핵심 — 공용 기기에서 같은 endpoint가 다른 계정으로 넘어갈 수 있어서,
  중복 행을 만드는 대신 `refresh(...)`로 소유자를 **재배정**한다. `consecutiveFailures`/`isDead()`로 연속
  실패 임계치(10회) 넘으면 죽은 구독으로 간주.
- **`PushSubscriptionRepository`**(`JpaRepository`): `findByEndpoint`, `findAllByBoormiId` 두 개뿐.
- **`PushSubscriptionService`**: 구독 등록(멱등 upsert)/해제/조회, 그리고 **전송 결과를 구독 행에 반영**.
  트랜잭션을 "전송 루프 전체"가 아니라 **조회 1회 + 결과 반영 1회**로 잘게 쪼갠 게 의도적 설계 —
  한 사용자가 여러 기기를 등록했을 때 외부 HTTP 왕복 여러 번을 하나의 트랜잭션으로 감싸면 그동안 DB
  커넥션을 계속 붙잡게 되기 때문. `PushSendOutcome`(enum: `SUCCESS/EXPIRED/RATE_LIMITED/PAYLOAD_TOO_LARGE
  /REJECTED/RETRIABLE_FAILURE`)에 따라 삭제해도 되는 경우와 절대 삭제하면 안 되는 경우를 구분해서
  처리한다(레이트리밋·설정 오류로 죽은 것도 아닌데 지워버리면 안 되니까).
- **`WebPushSender`**(`@ConditionalOnProperty(web-push.enabled=true)`일 때만 빈 등록): VAPID 서명 +
  aes128gcm 페이로드 암호화를 포함해 외부 푸시 서비스로 실제 HTTP 전송을 수행. `BouncyCastleProvider`를
  `Security.addProvider(...)`로 등록해 표준 JCE가 지원 안 하는 암호화 스위트를 쓴다. `PostConstruct`에서
  `PushService(publicKey, privateKey, subject)`를 초기화하다 키가 깨져 있으면 **일부러 기동을 실패**시킨다
  — 조용히 실패하게 두면 모든 전송이 매번 소리 없이 죽으므로, 차라리 배포 단계에서 잡히게 하려는 판단.
  TTL이 1분 이하인 알림(오퍼류)에는 `Urgency.HIGH`를 붙여 절전 상태의 기기를 실제로 깨우도록 유도한다.
- **`WebPushProperties`**(`@ConfigurationProperties(prefix = "web-push")`, record): `enabled/publicKey/
  privateKey/subject`. 비활성 상태에서도 바인딩은 되므로, 공개키 조회 API가 "미설정"임을 응답할 수 있다.
- **`SmsFallbackNotifier`**: 웹푸시/인앱 둘 다 안 닿을 때의 **최후 수단**. 의도적으로
  `NotificationChannel`에 넣지 않았다 — 트리거가 "배달 중 드리미 장시간 무소식" 단 하나뿐이라 파사드의
  정식 채널로 승격할 이유가 없고, 더 중요하게는 `NotificationPolicy` 결정표에 SMS 항목 자체가 없다는 게
  "어떤 이벤트도 실수로 유료 채널에 배선될 경로가 원천적으로 없다"는 안전장치가 된다(주석에 오배선 시
  하루 약 9,000건이 나갈 수 있다는 구체적 계산까지 남아 있음). 실패해도 재시도하지 않는다 — 호출자가
  시도 시점을 DB 컬럼에 영속 기록해 이것 자체가 중복 발송 방지(rate limit) 역할을 하기 때문.
- **`dto/`**: `PushEnvelope`(웹푸시 페이로드 — 프론트 서비스워커와의 계약이라 필드명을 함부로 못 바꿈,
  주문 상세를 절대 담지 않는 이유가 문서화됨), `PushSubscriptionRequest`/`PushUnsubscribeRequest`(브라우저
  `PushSubscription.toJSON()` 모양 그대로 받는 요청 DTO), `VapidPublicKeyDto`.
- **`PushSubscriptionController`**: `/api/v1/push/*`. 메서드명을 일부러 `subscribe`/`unsubscribe`가 아닌
  `createSubscription`/`deleteSubscription`으로 지은 이유가 재밌다 — springdoc이 메서드명을 operationId로
  쓰는데 `SseController.subscribe`와 이름이 겹치면 하나가 `subscribe_1`로 밀리고, 그러면 orval이 생성하는
  프론트 API 클라이언트에서 기존 SSE 오퍼레이션 이름이 조용히 바뀌어버린다.

---

## 9. `admin` — 인메모리 상태 진단

매칭·인증·SSE·레이트리밋 상태 상당수가 DB가 아니라 **JVM 힙 위의 `ConcurrentHashMap`/`Set`/`Queue`**에서
돈다. 그런데 그중 일부는 정리 경로가 없거나 특정 성공 경로에서만 청소되므로, "계속 늘기만 하고 안 줄어드는"
누수를 힙 덤프 없이 찾기 위한 조회 전용 관리자 API다.

```java
public interface InMemoryStateProbe {
    List<InMemoryStructureDto> inMemoryStructures();
}
```

- 상태를 들고 있는 빈(`ActiveSessionRegistry`, `NotificationService` 등)이 이 인터페이스를 구현해 자기
  자료구조 현황을 스스로 보고한다. **의존성 주입의 다형성 컬렉션 패턴**이 쓰인다 —
  `InMemoryStateController`가 `List<InMemoryStateProbe> probes`를 생성자로 받으면, Spring이 이 인터페이스를
  구현한 **모든 빈**을 자동으로 리스트에 모아 주입한다. 새 진단 대상을 추가하려면 그 클래스가 인터페이스만
  구현하면 되고, 컨트롤러 쪽 코드는 전혀 수정할 필요가 없다(개방-폐쇄 원칙).
- **`InMemoryStructureDto`**(record): 자료구조 하나의 `size` 뿐 아니라 상태별 집계(`breakdown: Map<String,
  Long>`)와 남아 있는 키 샘플(`samples`, 최대 5개)까지 담는다 — "왜 안 지워졌는지"를 크기 숫자 하나로는
  알 수 없어서다. 다만 `samples`는 키가 민감정보(휴대폰번호, 세션 ID 등)가 아닐 때만 채우고, 값(비밀번호·
  인증코드)은 어떤 경우에도 담지 않는다. 정적 팩토리 `ofMap`/`ofCollection`/`ofSize`로 상황별 생성 방법을
  나누고, `withBreakdown(...)`으로 집계를 나중에 덧붙일 수 있게 했다(불변 객체 + 파생 패턴, `notification`
  패키지의 `ChannelPlan`과 같은 스타일).
- **`InMemoryStateController`**: `@AdminUser` 파라미터(관리자 로그인 필수)로 보호되는 조회 전용
  `/api/v1/admin/inmemory` 엔드포인트. 강제 정리 기능은 없다 — 진행 중인 매칭/배달/세션을 건드릴 수 있어
  일부러 안 만들었다. `ClassUtils.getUserClass(probe)`로 실제 클래스명을 얻는 것도 세심한 부분 —
  `@Transactional` 빈은 스프링이 CGLIB 프록시로 감싸서 `getClass()`를 그냥 부르면
  `MatchingService$$SpringCGLIB$$0` 같은 이름이 나오는데, `getUserClass`는 프록시를 벗겨낸 원래 클래스를
  돌려준다.

---

## 정리: 패키지 간 의존 방향

```
domain/*  ──(에러코드 enum 구현)──▶  code
domain/*  ──(예외 던지기)────────▶  exception
domain/*  ──(@LoginUser 등)──────▶  session
domain/*  ──(NotificationService.notify)──▶ notification ──▶ sse
session   ──(로그인 사용자가 관리자인지 조회)──▶ domain.boormi (예외적 역참조)
sse       ──(연결 소유권 조회)───▶ session.ActiveSessionRegistry
admin     ──(진단 인터페이스로 각 빈을 수집)──▶ session, notification 등
```

`session → domain.boormi`처럼 global이 domain을 참조하는 경우가 있지만, 반대로 `domain.boormi`가
`global.session`을 참조하는 건 정상(공용 인프라니까)이고, 도메인끼리(`domain.boormi ↔ domain.dreami`)는
서로 참조하지 않는 게 원칙이다. 이 문서에서 다룬 9개 패키지가 그 원칙을 지키기 위한 "공용 어휘"에 해당한다.
