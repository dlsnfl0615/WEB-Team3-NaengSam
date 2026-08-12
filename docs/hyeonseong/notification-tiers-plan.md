# 3단 알림 체계 구현 계획 (인앱 / 웹푸시 / SMS)

> **이 문서는 자립형(self-contained)이다.** 이 계획을 만든 대화를 보지 않은 사람도 이 문서만으로 구현할 수 있도록, 프로젝트 오리엔테이션·용어·현재 상태 인벤토리·컨벤션을 모두 포함한다. 코드베이스를 이미 아는 사람은 §0~§2를 건너뛰어도 된다.
>
> 라인 번호 중 `~`가 붙은 것은 작성 시점의 근사치다. 파일명과 심볼명으로 찾을 것.

## 목차

- §0 프로젝트 오리엔테이션 (용어·구조·스택)
- §1 왜 이 작업을 하는가 (Context)
- §2 현재 알림 인프라 인벤토리
- §3 이 계획을 지배하는 제약 3가지
- §4 실현 가능성 결론 (tier별 솔직한 판정)
- §5 지켜야 할 프로젝트 컨벤션
- Phase 0 — PWA 아이콘 (선행 차단 요소)
- Phase 1 — 인앱 tier: 전역 토스트 스토어
- Phase 2 — 알림 파사드 (키스톤)
- Phase 3 — 서비스워커 마이그레이션
- Phase 4 — 매칭 유령 필터
- Phase 5a — 웹푸시 백엔드
- Phase 5b — 웹푸시 프론트 UX
- Phase 6 — SMS tier
- §6 지표
- §7 잘라내는 것 (재논의 방지)
- §8 의존 그래프 · 리스크
- §9 검증 절차

---

## §0 프로젝트 오리엔테이션

### 서비스

**쉼,부름** — 한국 시장을 대상으로 한 퀵배달 매칭 서비스. **모바일 웹 + PWA**가 타깃이다(네이티브 앱 없음. "웹앱"은 홈화면 추가 = PWA 설치를 말한다. Android·iOS 둘 다 대상).

### 용어 (코드 전반에 한국어 도메인 용어가 그대로 쓰인다)

| 용어 | 의미 |
|---|---|
| **부르미 (boormi)** | 배달을 요청하는 고객 |
| **드리미 (dreami)** | 배달을 수행하는 배달원/라이더 |
| **오퍼 (offer / MatchOffer)** | 특정 주문을 특정 드리미에게 제안한 것. TTL 30초 |
| **오퍼 그룹 (OrderOfferGroup)** | 주문 하나당 하나의 "방". 상태 `WAITING/OPEN/MATCHED/CANCELLED` |
| **콜** | UI에서 부르미의 배달 요청을 부르는 말 |

> **결정적으로 중요한 사실: `boormiId == dreamiId`다.** `User` 엔티티는 존재하지 않는다. 계정 행은 `BOORMI` 테이블이고, 드리미는 **같은 UUID를 PK로 쓰는 역할 확장 행**(`DREAMI` 테이블)이다. 즉 한 사람이 부르미이면서 드리미일 수 있고, SSE·세션·푸시의 사용자 식별자는 언제나 이 하나의 UUID다.

### 저장소 구조 (모노레포)

```
WEB-Team3-NaengSam/
├── backend/          Spring Boot 4.1.0, Java 21, Gradle (Groovy DSL)
├── frontend/         React 19 + TS + Vite 8, pnpm
├── matchingtest/     독립 Node 부하/매칭 하니스 (run.mjs)
├── monitoring/       Prometheus + Grafana (docker-compose)
├── docs/             설계 문서 (docs/donghyeok/sse/*, docs/hyeonseong/*)
└── .github/workflows/
```

### 백엔드 스택

- Spring Boot 4.1.0 / Java 21 / **servlet MVC (WebFlux 아님)**
- `spring-boot-starter-data-jpa`, validation, actuator, micrometer-prometheus, springdoc 3.0.2
- **`com.solapi:sdk:1.0.3` (이미 있음 — 현재는 가입 인증번호 전용)**, AWS S3 SDK
- DB: 운영 MySQL / 로컬 H2. **Flyway·Liquibase 없음.** 스키마는 손으로 쓴 `backend/sql/sym-boorm-ddl.sql`(~499줄, 약 30테이블)이고 `spring.jpa.hibernate.ddl-auto=none`
- 설정: `backend/src/main/resources/application.properties` 단일 파일(`.yml` 아님) + `application-loadtest.properties`. 비밀값은 전부 `${ENV_VAR}` 자리표시자, `backend/.env`(gitignore) / `.env.example`
- **인증: 쿠키 `HttpSession`. Spring Security 없음, JWT 없음.**
  - `global/session/LoginUserArgumentResolver` + `@LoginUser UUID userId` 가 현재 사용자 접근의 정석
  - `global/config/WebConfig`의 `LoginCheckInterceptor`가 `/api/**`에 인증을 **기본 필수**로 걸고, `@PublicApi`가 opt-out
  - `global/session/ActiveSessionRegistry`가 **사용자당 1 세션**을 강제. 로그인 시 이전 세션 무효화 + `sseEmitterRegistry.disconnectAll(userId, REPLACED_BY_LOGIN)`
- **비동기 인프라:** `@EnableScheduling` 켜짐, `@Async`/`@EnableAsync` 없음, Redis·MQ 없음. 커스텀 스레드는 (a) `MatchingEngine` 단일 consumer 스레드 (b) `MatchingActionScheduler`의 `DelayQueue` + 가상 스레드 (c) `SseService`의 단일 가상 스레드 `sse-sender`
- 배포: `backend/Dockerfile` → DockerHub → **EC2 단일 인스턴스**에서 `docker compose`. 단일 JVM이 전제다

### 프론트엔드 스택

- React 19 + TS + Vite 8 + **pnpm** (npm 쓰면 lockfile 때문에 실패), Tailwind CSS v4 (`@theme` 토큰), zustand 5, axios
- **`vite-plugin-pwa@1.3.0` 이미 설정됨** (`frontend/vite.config.ts`)
- API 클라이언트는 **orval 자동생성** — `pnpm api:gen`이 백엔드 `/v3/api-docs` → `src/shared/api/generated/**`. 생성물 직접 수정 금지
- 응답 envelope: 모든 응답이 `{ isSuccess, code, message, result }` → **실제 데이터는 `.result`**
- FSD 실용 3계층: `src/app` → `src/pages` → `src/shared`. import는 아래 계층으로만
- 배포: S3 + CloudFront. `.github/workflows/frontend-deploy.yml`이 `index.html`/`sw.js`/`registerSW.js`/`manifest.webmanifest`는 `no-cache,no-store,must-revalidate`로, 나머지는 `immutable`로 올린 뒤 CloudFront `/*` 무효화. **SW 배포 경로는 이미 운영 준비 상태다**
- `VITE_*` 환경변수는 **빌드 타임에 인라인**된다 → S3 정적 배포에서 런타임 변경 불가(변경엔 재빌드 필요)

---

## §1 왜 이 작업을 하는가 (Context)

지금 알림은 **인앱 토스트뿐**이고, 그 토스트조차 전역 스토어가 없어 5개 화면이 `useState` + `setTimeout` + 고정 위치 마크업을 각자 복붙하고 있다.

전달 경로는 SSE 단일 채널이라 **포그라운드 전용**이다. 드리미가 앱을 스와이프로 닫거나 탭이 메모리 압박으로 죽으면 오퍼는 그냥 사라진다 — `SseEmitterRegistry.send`가 `sse.events.dropped{reason=not_connected}`를 증가시키고 반환할 뿐이다. **이 지표가 이미 이 문제가 실제로 발생하고 있음을 증명한다.**

목표는 세 단계로 도달 범위를 넓히는 것:

1. **인앱** — 전역 토스트 스토어로 정리 (지금 있는 것의 정돈)
2. **웹푸시 (PWA)** — 앱이 닫혀 있어도 "열어보라"고 깨우기
3. **SMS (SOLAPI)** — 배달 중 드리미가 장시간 무소식일 때 문자

---

## §2 현재 알림 인프라 인벤토리

### SSE (지금 유일한 푸시 채널)

`backend/src/main/java/com/naengsam/quick/global/sse/`

| 파일 | 역할 |
|---|---|
| `SseController.java` | `GET /api/v1/sse/subscribe`, `produces=text/event-stream`, `@LoginUser UUID userId`. 사용자당 연결 상한 초과 시 **204 No Content**를 반환해 네이티브 `EventSource`의 자동 재연결을 멈춘다 |
| `SseService.java` | 공개 파사드. `subscribe(userId)`, `send(userId, SseEventType, payload)`. 전송을 **단일 가상 스레드**(`sse-sender`)로 오프로드 → 느린/죽은 클라이언트가 매칭 엔진을 막지 못하고 **이벤트 순서가 보존된다** |
| `SseEmitterRegistry.java` | `Map<UUID userId, Map<String connectionId, SseEmitter>>`. 연결마다 랜덤 `connectionId` → 여러 탭 공존. 상한을 `emitters.compute(...)` 안에서 원자적으로 강제, 초과 시 `null`. 접속 직후 `connected` 핸드셰이크 이벤트 전송. `@Scheduled(fixedRateString="${sse.heartbeat-interval}")`로 `:heartbeat` 주석 전송 + 실패 연결만 수거 |
| `SseEventType.java` | 인터페이스 `String eventName()`. 도메인별 enum이 구현 |
| `SseProperties.java` | `@ConfigurationProperties("sse")` record |
| `SseCloseReason.java` | `LOGOUT, SESSION_EXPIRED, REPLACED_BY_LOGIN, ACCOUNT_DISABLED, SHUTDOWN` |

`application.properties`: `sse.heartbeat-interval=25s`, `sse.connection-timeout=1h`, `sse.max-connections-per-user=5`

**Micrometer 지표(이미 있음):** `sse.connections.active`(Gauge), `sse.connections.opened/rejected/closed{reason}`, `sse.events.sent{event}`, `sse.events.dropped{reason=not_connected|send_failed}`. Grafana 대시보드 `monitoring/grafana/provisioning/dashboards/symboorm-http-sse.json`에 SSE 행이 이미 있다.

### 존재하는 SSE 이벤트 (11개 + 핸드셰이크)

`eventName()`은 모두 `name().toLowerCase()`다.

`domain/matching/event/MatchingEventType.java`:

| 이벤트 | 대상 | 페이로드 |
|---|---|---|
| `offer_popup` | 드리미 | `OfferPopupPayload` (주문 요약 + `offeredAt`/`expiresAt`) |
| `offer_closed` | 드리미 | `OfferClosedPayload(offerId, reason)` — `"선착순 마감"`/`"거절 완료"`/`"부르미가 주문을 취소함"` |
| `dreami_info` | 부르미 | `DreamiInfoPayload(offerId, orderId, dreamiId, pickupEtaMinutes, acceptedAt, expiresAt)` |
| `boormi_rejected` | 드리미 | `BoormiRejectedPayload(offerId, orderId)` |
| `offer_error` | 양쪽 | `NotificationErrorPayload(message)` |

`domain/delivery/event/DeliveryEventType.java`:

| 이벤트 | 대상 | 페이로드 |
|---|---|---|
| `delivery_location` | 부르미 | `DeliveryStatusResponseDto` — **초당 다발 이벤트** |
| `delivery_delivering` | 부르미 | 동일 |
| `delivery_cancelled` | 부르미 또는 드리미 | 동일 |
| `delivery_completed` | 부르미 | 동일 |
| `delivery_started_boormi` | 부르미 | 동일 |
| `delivery_started_dreami` | 드리미 | 동일 |
| `delivery_dreami_offline` | 부르미 | `DreamiOfflineDto(orderId, secondsSinceLastLocation)` |

+ 핸드셰이크 `connected` (payload `Map.of()`), `SseEmitterRegistry.connect`가 발행.

### 이벤트 발행 패턴 2가지 (둘 다 유지해야 한다)

**(a) 매칭 — 직접 전송.** `domain/matching/service/MatchingService.java`의 약 13곳(~137, 153, 194, 397, 421, 426, 448, 465, 473, 492, 535, 749, 755)에서 `sseService.send(...)`를 직접 호출한다. **이 코드는 `matching-engine` 단일 writer 스레드 위에서 돈다.** + `domain/matching/policy/assignment/MatchingPlanApplier.java:~98`에 1곳.

**(b) 배달 — 트랜잭션 안전.** 트랜잭션 안에서는 Spring 이벤트만 발행하고 커밋 후에 전송한다:

```java
// DeliveryService.java:~502
eventPublisher.publishEvent(new DeliveryNotificationEvent(userId, eventType, payload));

// DeliveryService.java:~506
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void sendAfterCommit(DeliveryNotificationEvent event) {
    sseService.send(event.userId(), event.eventType(), event.payload());
}
```

**(c) 스케줄 스캔.** `domain/delivery/service/DreamiOfflineDetector.java:~94`가 `delivery_dreami_offline`을 직접 전송.

→ **`sseService.send` 호출처 총 16곳.**

### 매칭 엔진 (푸시 설계에 직접 영향)

`domain/matching/service/MatchingService.java` (~782줄). 모든 상태가 in-memory `ConcurrentHashMap` (`offersById`, `offerIdsByDreamiId`, `orderOfferGroupsByOrderId`, `dreamiMap`)이고 **오퍼는 DB에 저장되지 않는다**(영속되는 매칭 산물은 `MATCHING` 테이블뿐).

```java
// MatchingService.java:~60-75
private static final Duration OFFER_TTL = Duration.ofSeconds(30);        // 드리미 응답 제한
private static final Duration BOORMI_OFFER_TTL = Duration.ofSeconds(30); // 부르미 응답 제한
private static final int MAX_OFFER_COUNT = 3;
private static final Duration REMATCH_SCAN_INTERVAL = Duration.ofMinutes(10);
```

만료 시각은 **절대 시각**으로 클라이언트에 전달된다(`OfferPopupPayload.expiresAt`, `DreamiInfoPayload.expiresAt`) — SSE 지연이 데드라인을 밀지 않게 하려는 의도적 설계다.

관련 클래스: `MatchingEngine`(단일 consumer 스레드, 매칭 상태의 **유일한** writer), `MatchingActionScheduler`(`DelayQueue` + 가상 스레드, `scheduleDreamiOfferTimeout`/`scheduleBoormiOfferTimeout`), `MatchingBatchDispatcher`(`matching.batch-window`=200ms 병합), `policy/assignment/*`, `policy/scoring/*`, `policy/eligibility/*`(`OutcomeCooldownOfferPolicy`가 거절/만료 후 10분 쿨다운).

**스냅샷 복구 API (푸시 설계의 핵심 자산):** `domain/matching/controller/MatchingController.java`의 `GET /api/v1/matching/current` → `CurrentMatchingStatusDto{pendingOffer, incomingDreami}`. SSE 페이로드와 **동일한 절대 `offeredAt`/`expiresAt`**을 반환한다.

### 프론트 SSE

`frontend/src/shared/lib/sse/`

| 파일 | 역할 |
|---|---|
| `SseProvider.tsx` | **탭당 정확히 하나의 `EventSource`**, `main.tsx`에서 `<App/>` 위에 마운트. `new EventSource(\`${VITE_API_BASE_URL ?? ""}/api/v1/sse/subscribe\`, { withCredentials: true })`. 화면은 연결을 만들지 않고 name→handler만 등록 |
| `SseContext.ts` | `SseStatus = "connecting"｜"connected"｜"reconnecting"｜"closed"`, `{ status, connected, subscribe, reconnect }` |
| `useSse.ts` | `useSse(handlers, { enabled })` |
| `useSseReconnectSync.ts` | 갭 복구 — `reconnecting` 동안 3초마다 `recover()` 폴링, `reconnecting|closed → connected` 전이 시 1회 `recover()`. 최초 `connecting → connected`는 의도적으로 건너뜀 |
| `SseStatusBanner.tsx` | `status === "closed"`일 때만 모달 |

재연결(`SseProvider.tsx:~104-121`) — 커스텀 백오프 없이 네이티브 `EventSource`에 기대고 `readyState`로 치명/일시를 구분:

```ts
source.onerror = () => {
  if (source.readyState === EventSource.CLOSED) {
    setStatus("closed"); source.close(); matching.stopMatchingPolling();   // 예: 204 상한 초과
  } else {
    setStatus("reconnecting"); matching.startMatchingPolling();            // 브라우저가 재시도, 그 사이 폴링
  }
};
```

`connected` 수신 시 폴링을 멈추고 `syncCurrentMatching()`을 1회 호출한다.

구독자: `pages/matching/ui/MatchingPopup.tsx:~83-99`(전역 팝업, 매칭 이벤트 전부 + `delivery_started_*`), `pages/delivery-detail/ui/RealDeliveryTracking.tsx:~227-281`, `pages/delivery-track/ui/DeliveryTrackScreen.tsx:~140-160`.

상태: `frontend/src/shared/store/matchingStore.ts` — zustand. `pendingOffer`/`incomingDreami`/`message` + `receiveOfferPopup/Closed/BoormiRejected/DreamiInfo/OfferError` + `startMatchingPolling/stopMatchingPolling/syncCurrentMatching`. 모듈 스코프에 `let pollTimer`, `let syncing` 가드.

### 현재 토스트 (전역 스토어 없음)

`frontend/src/shared/ui/Toast/Toast.tsx` — **순수 표현 컴포넌트**. props `{ icon?: IconName, title, description?, action? }`, 기본 아이콘 `'bell'`. `shared/ui/index.ts` 배럴에서 export.

`<ToastProvider>`도, `useToast()`도, 큐도, portal도 없다. 각 호출처가 고정 위치 래퍼를 손으로 복붙한다:

```tsx
{toast && (
  <div className="fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
    <Toast icon="bell" title={toast.title} description={toast.description} />
```

중복 위치: `pages/home/ui/HomeScreen.tsx:~71`, `pages/matching/ui/MatchingScreen.tsx:~241`, `pages/matching/ui/MatchingPopup.tsx:~124`, `pages/delivery-detail/ui/RealDeliveryTracking.tsx:~378`, `pages/delivery-track/ui/DeliveryTrackScreen.tsx:~274`. `TRANSIENT_TOAST_MS`도 페이지마다 따로 정의돼 있다.

### PWA 현재 상태

`frontend/vite.config.ts:14-39`:

```ts
VitePWA({
  registerType: 'autoUpdate',   // SW 자동 등록·갱신 (injectRegister:'auto' 기본)
  includeAssets: ['favicon.svg', 'apple-touch-icon-180x180.png'],
  manifest: {
    name: '쉼,부름', short_name: '쉼,부름', lang: 'ko',
    start_url: '/', scope: '/', display: 'standalone', orientation: 'portrait',
    background_color: '#f7f8fa', theme_color: '#0d1b3d',
    icons: [
      { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
      { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
      { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  },
})
```

- **`generateSW` 모드이고 소스 SW 파일이 없다** (`src/sw.ts` 없음, 빌드 산출물 `dist/sw.js`만 존재)
- `index.html`에 `viewport-fit=cover`, `theme-color`, `apple-mobile-web-app-capable=yes`, `apple-mobile-web-app-status-bar-style`, `apple-mobile-web-app-title`, `<link rel="apple-touch-icon" href="/apple-touch-icon-180x180.png">`
- `frontend/src/shared/ui/ScreenShell/ScreenShell.tsx:~16`이 PWA standalone 세이프에어리어를 흡수
- **`Notification`/`requestPermission`/`PushManager`/`showNotification`/VAPID/`beforeinstallprompt` 코드가 전 코드베이스에 0건**

**깨진 부분:** `frontend/public/`에는 `favicon.svg`, `icons.svg`, `pwa-192x192.png`(827KB!)만 있다. **`pwa-512x512.png`와 `apple-touch-icon-180x180.png`는 존재하지 않는데 참조된다.**

### SMS 현재 상태

SOLAPI가 이미 통합돼 있지만 **가입 전화번호 인증 전용**이다.

- `backend/build.gradle`: `com.solapi:sdk:1.0.3`
- `domain/user/sms/SmsSender.java` — 인터페이스 `send(String toPhone, String text)`
- `domain/user/sms/SolapiSmsSender.java` — `@ConditionalOnProperty(name="solapi.enabled", havingValue="true")`, `@PostConstruct`에서 `SolapiClient.INSTANCE.createInstance(apiKey, apiSecret)`, 실패 시 `BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR)`
- `domain/user/sms/DevSmsSender.java` — `havingValue="false", matchIfMissing=true`, 코드를 로그만 찍는다
- `domain/user/sms/SolapiProperties.java` — `@ConfigurationProperties("solapi")`
- 유일한 호출처: `domain/user/service/SmsVerificationService.java:~36` — `smsSender.send(phone, "[쉼,부름] 인증번호 [" + code + "]를 입력해 주세요.")`
- 남용 통제: `domain/user/service/SmsSendRateLimiter.java` (IP별/번호별/전역, in-memory, `@Scheduled` 시간별 정리), `verification.*` 프로퍼티 (`code-ttl=5m`, `resend-cooldown=60s`, `phone-max=5/24h`, `global-max=1000/24h`)
- env: `SOLAPI_ENABLED`, `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_FROM`

`domain/boormi/entity/Boormi.java`에 `phoneNumber`가 있다 — `phone_number varchar(50) NOT NULL`, UNIQUE(`UQ_BOORMI_PHONE_NUMBER`, DDL ~327줄), **가입 시 인증됨**(단, `phoneVerified` 컬럼은 없다. 인증 상태는 `VerificationCodeStore`의 in-memory 맵이고 가입 완료 시 소비된다 → BOORMI에 있는 번호는 구조적으로 인증된 번호다).

**Twilio·NHN·Slack·SMTP·카카오 알림톡은 전혀 없다.**

### 이미 있는 "드리미 무소식" 감지 (SMS tier의 토대)

`domain/delivery/service/DreamiOfflineDetector.java`:

```java
:42  private static final Set<DeliveryCd> TRACKED_STATUSES = ...   // PICKUP_NORMAL, PICKUP_DELAYED, DELIVERING
:47  private final Duration offlineThreshold;
:50  private final Set<UUID> notifiedOrders = ConcurrentHashMap.newKeySet();   // ← 재시작 시 소실
:53  @Value("${delivery.dreami-offline-threshold}") Duration offlineThreshold
:64  @Scheduled(fixedDelayString = "${delivery.dreami-offline-scan-interval}")  // 5초
:77  LocalDateTime threshold = LocalDateTime.now().minus(offlineThreshold);     // 30초
:79  deliveryRepository.findStaleLocationDeliveries(TRACKED_STATUSES, threshold);
:86  notifiedOrders.retainAll(staleOrderIds);   // 자가 치유
:89  if (!notifiedOrders.add(delivery.getOrderId())) { ... }   // 중복 억제
```

`domain/delivery/repository/DeliveryRepository.java:42-46`:

```java
// lastLocationDtm IS NOT NULL 조건으로 '첫 위치가 아직 안 온 배달'은 제외한다 —
@Query("... AND d.lastLocationDtm IS NOT NULL AND d.lastLocationDtm < :threshold")
List<Delivery> findStaleLocationDeliveries(@Param("statuses") Collection<DeliveryCd> statuses, ...);
```

`domain/delivery/entity/Delivery.java`:

```java
:52  @Column(name = "last_location_dtm")
:53  private LocalDateTime lastLocationDtm;   // 드리미가 마지막으로 위치를 전송한 시각 — GPS 끊김(무소식) 판정 기준
:89  this.lastLocationDtm = LocalDateTime.now();   // 위치 갱신 경로
```

`backend/sql/sym-boorm-ddl.sql:278` — `last_location_dtm timestamp NULL COMMENT '드리미가 마지막으로 위치를 전송한 시각(GPS 끊김 판정용)'`

**→ SMS tier는 임계값 하나를 추가하는 수준으로 붙는다. 새 상태 추적 인프라가 필요 없다.**

### 없는 것

- `notification`/`push`/`token`/`device` 관련 테이블·엔티티·리포지토리 **전무** (`sym-boorm-ddl.sql` grep 0건)
- 이름에 "notification"이 들어가는 것은 `DeliveryNotificationEvent`(in-process Spring 이벤트)와 `NotificationErrorPayload`(SSE 에러 페이로드 record)뿐 — 둘 다 영속되지 않는다
- SSE 이벤트 ID / `Last-Event-ID` / 재생(replay) 없음 → **끊긴 동안의 이벤트는 소실된다**(`docs/donghyeok/sse/sse-current-behavior.md` §4에 명시)
- 기기별 식별자 없음. SSE의 서버 내부 `connectionId`가 유일하고 영속되지 않는다

### 알아둘 기존 문제 (이 계획의 범위 외지만 근처다)

`domain/delivery/controller/DeliveryTestController.java:~103`의 `GET /api/v1/delivery/test/subscribe/{userId}`가 클래스에 `@PublicApi`가 붙어 있고 **`@Profile("local")`이 ~28줄에서 주석 처리되어 있다** → 모든 프로파일에서 인증 없이 접근 가능. 별도 이슈로 처리할 것.

---

## §3 이 계획을 지배하는 제약 3가지

### ① 오퍼 TTL 30초 vs 푸시 지연 → 푸시는 오퍼를 실어 나를 수 없다

`OFFER_TTL = 30s`인데:
- **iOS는 `userVisibleOnly: true`가 강제**라서 무음 데이터 푸시가 **아예 없다**. 알림 자체가 UX이고, 앱 상태는 어차피 API에서 와야 한다. 푸시가 오퍼를 나르는 설계는 Android에서만 되므로 두 가지 동작을 유지해야 한다
- FCM p50은 1초 미만이지만 **Android Doze는 일반 우선순위 메시지를 수 분까지** 붙잡는다. iOS는 알림 예산과 저전력 상태로 스로틀한다
- 90초 전에 만료된 오퍼를 배달하면 드리미가 수락을 눌렀다가 `OFFER_ERROR`를 받는다. **알림이 없는 것보다 나쁘다**

**→ 푸시는 "앱 열어주세요" wake-up 봉투만 보낸다.** 앱이 열리면 이미 있는 `GET /api/v1/matching/current` 스냅샷이 진짜 상태를 가져온다. `SseProvider`의 `connected` 핸들러가 이미 `matchingStore.syncCurrentMatching()`을 호출하고, 그 API는 SSE 페이로드와 같은 절대 `expiresAt`을 반환한다 → **"앱을 열면 정확한 현재 상태, 만료됐으면 아무것도 없음"이 신규 코드 0으로 이미 동작한다.** 푸시는 사용자가 앱을 열게만 하면 된다.

또한 절대 `expiresAt` 덕분에 낡은 상태가 자기 교정된다 — 만료 5초 전에 가져온 스냅샷은 5초 카운트다운을 보여준다. 정직하다.

### ② 매칭 엔진 스레드에 블로킹 I/O 금지

`sseService.send` 호출 16곳 중 13곳이 `MatchingService`, 즉 `matching-engine` **단일 writer 스레드** 위다. 여기서 FCM HTTP POST(p99 ~1-2초)를 하면 매칭 엔진 전체가 멈춰 다른 모든 주문의 오퍼와 타임아웃이 지연된다.

### ③ PWA 아이콘이 지금 깨져 있다 → tier 2가 원천 차단 상태

- **Android 설치 불가.** Chrome은 ≥192 **및** ≥512 아이콘을 요구한다. 512에서 404가 나면 manifest가 자격을 잃어 설치 프롬프트도, `beforeinstallprompt`도, standalone 설치도 없다. (설치 없이도 Android는 탭에서 푸시가 되지만 PWA 스토리는 성립하지 않는다)
- **iOS 웹푸시는 홈화면 설치가 필수**인데, iOS 홈화면 추가는 아이콘 대신 스크린샷으로 폴백한다 — 사용자에게 신뢰를 요구하는 바로 그 순간에 "홈 화면에 추가" 안내가 고장 난 것처럼 보인다
- `pwa-192x192.png`가 192px짜리인데 **827KB** — 약 30배 과대하고, precache되므로 모든 설치가 이 비용을 낸다

**아무것도 시끄럽게 에러를 내지 않아서 놓치기 쉽다.**

---

## §4 실현 가능성 결론

| Tier | 판정 |
|---|---|
| **1. 인앱 (SSE + 토스트)** | **잘 된다. 이게 제품이다.** SSE 인프라가 진짜 좋다 — 연결별 레지스트리, 하트비트 유령 정리, Micrometer 커버리지, 단일 writer 순서 보장, 페이로드의 절대 `expiresAt`, 스냅샷 복구. 이 tier가 알림 가치의 ~95%를 이미 나르고 계속 나를 것이다. 남은 갭은 미용(중복 배관, 큐·중복제거 없음)과, 알림과 무관한 실질 문제 하나(죽은 드리미에게 오퍼 보내기 → Phase 4) |
| **2. 웹푸시** | **부분적으로 됨. 불안정할 것. 과대 포장하지 말 것.** |
| **3. SMS** | **안정적으로 된다. 플래그 뒤에 두고 배포. 리스크는 기술이 아니라 요금.** |

### tier 2를 플랫폼별로 솔직하게

- **Android Chrome: 잘 된다.** 수 초 내 안정적 도착, 설치 없이 일반 탭에서도 동작, 한국 드리미 기기의 다수를 커버. **이것만으로 이 tier를 만들 이유가 된다**
- **iOS Safari: 대부분 사용자에게 사실상 안 된다.** 홈화면 설치가 필수고, 설치를 프롬프트로 유도할 방법이 없으며, 현실적으로 3단계 일러스트 안내를 따르는 사람은 소수다. 설치한 사람도 백그라운드 전달이 알림 예산과 저전력 모드로 스로틀된다. **iOS 사용자 커버리지는 한 자리~낮은 두 자리 퍼센트를 예상하라.** 코드가 Android와 같으니 만들되, **설치 안내 카드 외의 iOS 전용 배관은 만들지 말고, iOS 푸시를 기능 목록에 넣지 말 것**
- **인앱브라우저(카카오톡): 영구 0%.** 우회법이 존재하지 않는다. 카카오톡으로 유입된 사용자에게 tier 2는 존재하지 않으므로, "외부 브라우저로 열기" 카드는 장식이 아니라 **하중을 받는 인프라**다
- **30초 오퍼에 대해서는 푸시가 자주 너무 늦다.** `TTL: 30`을 걸면 늦게 오는 대신 **폐기**되는 쪽이 많아진다 — 올바른 실패 모드지만 드리미가 알지 못한다는 뜻이기도 하다

> **문구 규율 (CS 컴플레인 방지):**
> ✅ "앱을 닫아도 **놓친 알림을 알려드려요**"
> ❌ "앱을 닫아도 **콜을 받을 수 있어요**" ← 거짓이다

---

## §5 지켜야 할 프로젝트 컨벤션

`backend/CLAUDE.md` / `frontend/CLAUDE.md` 에서 이 작업에 해당하는 것만 발췌.

### 백엔드

- **YAGNI 우선.** 새 추상화는 중복이 두 번 나타난 뒤에
- 예외는 `BusinessException` + 도메인별 `<Domain>ErrorCode`
- 컨트롤러는 envelope 없는 맨 DTO를 반환(래핑은 전역 처리)
- 엔티티는 정적 팩토리 (`Xxx.create(...)`), `@NoArgsConstructor(access = PROTECTED)`, `@Getter`
- UUID PK: `@JdbcTypeCode(SqlTypes.BINARY)` + `@Column(columnDefinition = "BINARY(16)")`
- **네이티브 쿼리는 DDL의 UPPERCASE 테이블명을 써야 한다** (운영 Linux MySQL이 `lower_case_table_names=0`)
- 테스트 메서드명은 한글 BDD
- **새 테이블은 `backend/sql/sym-boorm-ddl.sql`에 추가하고, 이미 배포된 DB용 `ALTER TABLE` 주석도 함께 남긴다** (Flyway가 없어 수동 적용이다)
- 타입 세이프 설정은 `@ConfigurationProperties` record + `QuickApplication`의 `@EnableConfigurationProperties`에 등록 (기존: `SolapiProperties`, `VerificationProperties`, `UploadProperties`, `SseProperties`, `MatchingPolicyProperties`)
- **기능 플래그 + dev double 패턴**(이 프로젝트의 관용구): `solapi.enabled`/`kakao.enabled`/`upload.s3-enabled`가 각각 인터페이스 뒤에서 실제 구현과 dev 구현을 고른다 (`SolapiSmsSender`↔`DevSmsSender`, `S3Uploader`↔`DevUploader`)
- `global/config/ClockConfig.java`가 주입 가능한 `Clock`을 제공 — 매칭 코드는 테스트 가능성을 위해 `LocalDateTime.now(clock)`을 쓴다. 새 스케줄러도 이걸 따를 것

### 프론트엔드

- **pnpm 필수.** `pnpm install --ignore-scripts` → `pnpm dev` / `pnpm build`(=`tsc -b && vite build`) / `pnpm lint`
- **토큰만 사용.** 색·반경·그림자는 `src/app/styles/theme.css`의 `@theme` 토큰 유틸만 (`bg-navy-900` ✅ / `bg-[#0d1b3d]` ❌)
- 아이콘은 `<Icon name>`. `<img>`·인라인 `<svg>` 금지
- **한 파일은 컴포넌트만 export** (`react-refresh/only-export-components`). 상수·헬퍼는 별도 파일
- 조건부 클래스는 `cn()` 헬퍼
- 새 `shared/ui` 컴포넌트 절차: 파일 생성 → `shared/ui/index.ts` 배럴에 export → `frontend/design.md` 컴포넌트 카탈로그에 한 줄 → `pnpm lint && pnpm build`
- import alias `@/` → `src/`
- 서버 상태는 `shared/store/`의 zustand에, DTO→화면 타입은 어댑터 함수로
- 백엔드 스펙 변경 후 `pnpm api:gen`
- 작업 종료 시 항상 `pnpm lint && pnpm build` 통과

---

# Phase 0 — PWA 아이콘 (선행 차단 요소, 반나절, 리스크 0)

**§3-③ 때문에 tier 2의 어떤 것도 이게 없으면 의미가 없다.**

기존 브랜드 소스(`frontend/public/favicon.svg`)에서 생성, 각 <30KB로 최적화:

| 파일 | 비고 |
|---|---|
| `frontend/public/pwa-192x192.png` | 기존 827KB를 재최적화 |
| `frontend/public/pwa-512x512.png` | 신규. Android 설치 자격의 필수 조건 |
| `frontend/public/pwa-512x512-maskable.png` | 신규. **~20% 패딩 있는 전용 파일** |
| `frontend/public/apple-touch-icon-180x180.png` | 신규. `index.html`과 `includeAssets`가 참조 |

`frontend/vite.config.ts:31-36`의 maskable 항목이 지금 평범한 512를 재사용하고 있다 → 전용 파일로 교체. **평범한 아이콘을 maskable로 쓰면 Android 런처가 로고를 잘라먹는다.**

---

# Phase 1 — 인앱 tier: 전역 토스트 스토어 (프론트만, 완전 독립)

### `frontend/src/shared/store/toastStore.ts` (신규)

`shared/store/matchingStore.ts` 스타일 그대로 — `create<State>((set, get) => ({...}))`, 스토어와 각 액션에 한글 Javadoc, **타이머는 렌더 무관하므로 상태가 아니라 모듈 스코프**(`matchingStore`의 `let pollTimer`와 동일 방식).

```ts
import { create } from "zustand";
import type { ReactNode } from "react";
import type { IconName } from "@/shared/ui";

/** 자동 소멸 기본 시간(ms). 기존 5개 화면의 TRANSIENT_TOAST_MS와 동일값. */
export const TOAST_DURATION_MS = 3_000;
/** 동시에 쌓이는 토스트 상한. 넘으면 가장 오래된 것을 밀어낸다. */
const MAX_TOASTS = 3;

export interface ToastItem {
  id: string;
  icon?: IconName;
  title: string;
  description?: ReactNode;
  /** true면 자동 소멸하지 않고 닫기 버튼으로만 사라진다(기존 MatchingScreen의 persistent와 동일). */
  persistent?: boolean;
  durationMs?: number;
  /** 같은 key의 토스트는 새로 쌓지 않고 교체한다. SSE 중복 수신·재연결 폴링이 같은 안내를 두 번 띄우는 것을 막는다. */
  dedupeKey?: string;
}

interface ToastState {
  toasts: ToastItem[];
  /** 토스트를 띄우고 id를 반환한다. persistent가 아니면 durationMs 후 자동 소멸. */
  show: (toast: Omit<ToastItem, "id">) => string;
  dismiss: (id: string) => void;
  /** 로그아웃·화면 전환 시 남은 토스트 전체 제거. */
  clear: () => void;
}

/** 자동 소멸 타이머. 렌더와 무관하므로 store 상태가 아니라 모듈 스코프로 둔다(matchingStore의 pollTimer와 동일 방식). */
const timers = new Map<string, ReturnType<typeof setTimeout>>();
```

동작:
- `show` — `dedupeKey`가 기존 토스트와 일치하면 **제자리 교체 + 타이머 리셋**(append 아님). 아니면 append하고 `MAX_TOASTS` 초과분을 앞에서 잘라낸다
- `dismiss` — 타이머 정리 + 필터
- `clear` — 모든 타이머 정리

**`dedupeKey`가 핵심이다** — SSE 중복 수신과 3초 주기 재연결 폴링이 같은 안내를 반복 발화하는 것을 막는다.

### `frontend/src/shared/ui/Toast/ToastViewport.tsx` (신규)

복붙된 고정 위치 래퍼를 여기로 흡수하고, **기존 dumb `Toast.tsx`는 변경 없이 재사용**:

```tsx
<div className="pointer-events-none fixed inset-x-0 top-4 z-50 mx-auto flex max-w-[420px] flex-col gap-2 px-4">
  {toasts.map((t) => (
    <div key={t.id} className="pointer-events-auto">
      <Toast icon={t.icon} title={t.title} description={t.description}
        action={t.persistent ? <CloseButton onClick={() => dismiss(t.id)} /> : undefined} />
    </div>
  ))}
</div>
```

`shared/ui/index.ts` 배럴에 export, `frontend/design.md` 컴포넌트 카탈로그에 한 줄 추가(§5 절차), `main.tsx`에 한 번 마운트:

```tsx
<SseProvider>
  <App />
  <SseStatusBanner />
  <ToastViewport />
  <PushNavigationBridge />   {/* Phase 3에서 추가 */}
</SseProvider>
```

### 마이그레이션 대상 — 5곳 중 3곳만

진짜 중복인 것과 겉만 닮은 것을 구분한다.

| 위치 | 조치 |
|---|---|
| `pages/home/ui/HomeScreen.tsx:~39,~60,~71` | **이전.** 교과서적 일회성 토스트. `location.state.dreamiVerificationSubmitted`에서 시드하므로 router state를 지우는 같은 effect에서 `show()` 호출 |
| `pages/matching/ui/MatchingScreen.tsx:~93,~99,~241` | **이전.** 이미 `persistent`를 쓰고 있어 스토어가 그대로 모델링 |
| `pages/delivery-detail/ui/RealDeliveryTracking.tsx:~154,~203,~378` | **이전.** `showTransientToast` → `useToastStore.getState().show`. `toastTimer` ref와 cleanup effect 삭제. 주의: `showCancellationModal`이 블로킹 모달을 열기 전에 타이머와 토스트를 정리하는데 → `clear()`로 |
| `pages/matching/ui/MatchingPopup.tsx:~124` | **건드리지 않음.** 하단 고정이고 오퍼 시트 레이아웃(`ds-sheet-up`, `pb-[calc(env(safe-area-inset-bottom)...)]`)에 결합돼 있으며, 전용 오버레이의 "카드 없음" 상태로 `matchingStore.message`를 렌더한다. 상단 viewport로 옮기는 건 리팩터가 아니라 UX 변경 |
| `pages/delivery-track/ui/DeliveryTrackScreen.tsx:~274` | **건드리지 않음.** `locationError` → "GPS를 허용해주세요" 조건 기반 상시 배너지, 큐잉되는 일회성 알림이 아니다. 변환하면 권한 상태가 바뀔 때마다 push/pop해야 한다 |

순수익: 중복 타이머 배관 ~60줄 삭제 + 스택/중복제거/개수상한이 한 곳에 모임.

**범위 외:** `matchingStore.message`를 `toastStore`로 교체하는 것. `MatchingPopup`의 하단 시트 레이아웃에 묶여 있고 `receiveOfferClosed`/`receiveBoormiRejected`/`receiveOfferError`/`goOnline`/모든 `catch`가 소비한다. 선택적 후속 정리로 남긴다.

---

# Phase 2 — 알림 파사드 (보이는 변화 없음, 그러나 키스톤)

## 왜 필요한가

지금 `sseService.send`가 16곳에서 직접 호출된다. 채널을 2개 더 붙이려면 파사드가 필요하다. 아니면 16곳에 푸시 호출을 각각 추가하게 되고, 그중 13곳은 §3-② 때문에 애초에 블로킹 I/O를 할 수 없는 자리다.

## 새 패키지: `backend/src/main/java/com/naengsam/quick/global/notification/`

```
NotificationService.java     — 유일한 공개 파사드
NotificationChannel.java     — enum { IN_APP, WEB_PUSH }   ← SMS는 여기 없다 (Phase 6 참조)
NotificationPolicy.java      — 채널 결정표
ChannelPlan.java             — record
NotificationErrorCode.java
// Phase 5a에서 추가: PushSubscription, PushSubscriptionRepository,
//                    PushSubscriptionService, PushSubscriptionController,
//                    WebPushSender, WebPushProperties
```

**엔티티·컨트롤러를 `global/`에 두는 것은 `backend/CLAUDE.md`의 "엔티티는 domain 하위" 규칙에서 의도적으로 벗어나는 것이다.** 근거: `global/sse/SseController.java`가 이미 선례이고, 푸시 구독은 모든 도메인의 알림이 타는 횡단 인프라다. `domain/notification/`에 두면 `domain/matching → domain/notification` 역방향 의존이 생겨 "도메인끼리 독립 유지" 규칙을 깬다. **패키지 Javadoc에 이 근거를 적을 것.**

## 파사드

시그니처를 `SseService.send`와 **동일하게** 유지하는 것이 마이그레이션 안전성의 핵심이다.

```java
/**
 * 도메인이 알림을 보낼 때 쓰는 유일한 진입점.
 *
 * <p><b>제약:</b> notify()는 매칭 엔진 단일 writer 스레드(matching-engine)에서 호출된다.
 * 따라서 이 클래스 안에서 블로킹 I/O(FCM·SOLAPI HTTP)를 절대 동기로 실행하지 말 것.
 * IN_APP은 SseService의 sse-sender 가상 스레드로, WEB_PUSH는 아래 outbound executor로 오프로드한다.
 */
@Slf4j @Component @RequiredArgsConstructor
public class NotificationService {

    private final SseService sseService;
    private final NotificationPolicy policy;
    private final ObjectProvider<WebPushSender> webPushSender;   // 비활성이면 비어 있다

    /** SseService.send와 동일 시그니처 — 16개 호출처를 기계적으로 치환할 수 있게 한 것이다. */
    public void notify(UUID userId, SseEventType eventType, Object payload) { ... }

    /** 오퍼 대상 선정 등에서 "지금 실시간으로 닿을 수 있나"를 판단한다(Phase 4). */
    public boolean isReachableNow(UUID userId) { return sseService.isConnected(userId); }
}
```

### 스레드 모델 — §3-② 의 해법

- **`IN_APP`은 기존 `SseService.send`에 위임하고 그 경로를 절대 바꾸지 않는다.** 단일 `sse-sender` 가상 스레드가 사용자별 이벤트 순서를 보장하고, `MatchingPopup`이 그걸 전제로 동작한다(`offer_popup`이 `offer_closed` 뒤에 오면 안 된다)
- **`WEB_PUSH`는 `NotificationService`가 소유하는 별도 유계 executor로:**

```java
private final ExecutorService outbound = Executors.newFixedThreadPool(
        4, r -> Thread.ofVirtual().name("notification-outbound-", 0).unstarted(r));

@PreDestroy void shutdown() { outbound.shutdown(); }
```

4개는 의도적이다 — 오퍼 폭주 시 스레드 폭발이 없는 유계 병렬성이고, 가상 스레드는 블로킹 HTTP 중 unmount된다. **`newVirtualThreadPerTaskExecutor()`를 쓰지 말 것** — 무제한이라 푸시 서비스 장애 시 태스크가 수천 개 쌓인다. 큐 깊이에 `Gauge`를 달아 장애가 기존 Grafana에서 보이게 한다.

푸시 실패는 절대 전파 금지 — `sendPushQuietly`가 전부 catch하고 `notification.dropped{channel=web_push,reason=...}`를 증가시키고 `warn`에 요약만 남긴다(`DreamiOfflineDetector.detectOfflineDreamis`와 같은 규율).

## 채널 결정표 (`NotificationPolicy`)

```java
@Component
public class NotificationPolicy {
    /**
     * SSE 이벤트 이름 → 채널 계획. 키를 도메인 enum이 아니라 이벤트 이름(String)으로 두는 이유:
     * global이 domain을 import하는 역방향 의존을 만들지 않기 위해서다. 이 이름은 이미
     * 프론트가 addEventListener로 구독하는 와이어 계약이라 새로 만든 식별자가 아니다.
     *
     * 표에 없는 이벤트는 IN_APP만 — 새 이벤트가 실수로 푸시 채널을 타지 않는 안전 기본값이다.
     */
    private static final Map<String, ChannelPlan> PLANS = Map.ofEntries(...);

    public ChannelPlan planFor(SseEventType type) {
        return PLANS.getOrDefault(type.eventName(), ChannelPlan.inAppOnly());
    }
}
```

`ChannelPlan`은 `record ChannelPlan(Set<NotificationChannel> channels, String pushTitle, String pushBody, Duration pushTtl)`.

| 이벤트 | 채널 | 푸시 TTL | 푸시 문구 | 비고 |
|---|---|---|---|---|
| `offer_popup` | IN_APP + WEB_PUSH | **30s** | "새 배달 요청이 왔어요" / "앱을 열어 확인해주세요" | wake-up만 |
| `dreami_info` | IN_APP + WEB_PUSH | **30s** | "드리미를 찾았어요" / "앱을 열어 확정해주세요" | |
| `delivery_started_boormi` | IN_APP + WEB_PUSH | 10m | "배달이 시작됐어요" / "실시간으로 위치를 확인해보세요" | |
| `delivery_started_dreami` | IN_APP + WEB_PUSH | 10m | "배달이 시작됐어요" / "픽업지로 이동해주세요" | |
| `delivery_delivering` | IN_APP + WEB_PUSH | 10m | "픽업이 완료됐어요" / "물품이 도착지로 이동 중이에요" | |
| `delivery_completed` | IN_APP + WEB_PUSH | 1h | "배달이 완료됐어요" / "앱에서 배달 결과를 확인해주세요" | |
| `delivery_cancelled` | IN_APP + WEB_PUSH | 1h | "배달이 취소됐어요" / "앱에서 환불 내역을 확인해주세요" | |
| `delivery_location` | **IN_APP 영구** | — | — | 초당 이벤트. 켜면 즉시 FCM 쿼터 터지고 알림 도배 |
| `offer_closed` | IN_APP 전용 | — | — | 앱 열려 있을 때만 의미 있음 |
| `boormi_rejected` | IN_APP 전용 | — | — | 동일 |
| `offer_error` | IN_APP 전용 | — | — | 동일 |
| `delivery_dreami_offline` | IN_APP 전용 | — | — | 부르미가 화면을 보고 있음 |

### 푸시 TTL을 오퍼 TTL과 같게 두는 것이 핵심 안전장치다

web-push `TTL` 헤더가 30이면 푸시 서비스가 전달 못 한 오퍼 wake-up을 **폐기**한다. 4분 뒤 폰이 깨어날 때 배달되어 사용자가 앱을 열었더니 아무것도 없는 최악의 실패 모드를 이 헤더 하나가 막는다. 오퍼는 `Urgency: high`, 배달 상태 이벤트는 기본값.

### 푸시 본문에 주문 상세를 넣지 않는다

수수료·주소·품목 없음. 게으름이 아니다: (a) 30초 TTL을 견디는 유일한 설계가 순수 wake-up이고 (b) 잠금화면에 주문 정보가 새지 않고 (c) `global/notification`이 도메인 DTO를 import하지 않아도 된다(그래서 정적 문구 상수로 충분하다).

### 회귀 방어 테스트 (`NotificationPolicyTest`, 한글 BDD)

```java
@Test void 모든_매칭_배달_이벤트가_채널_결정표에_등록되어_있다() { ... }
@Test void delivery_location은_절대_WEB_PUSH_채널을_포함하지_않는다() { ... }
```

두 번째는 회귀 울타리다 — `delivery_location`을 푸시로 뒤집으면 배달 하나당 수천 개 알림이 생성된다.

## 16개 호출처 교체 (동작 무변화)

`notify(UUID, SseEventType, Object)`가 `SseService.send`와 **동일 시그니처**이고 정책 기본값이 모든 이벤트를 `IN_APP`으로 두므로, 이것은 **동작이 증명적으로 동일한** 기계적 필드·이름 치환이다.

| 파일 | 변경 |
|---|---|
| `domain/matching/service/MatchingService.java` | 필드 `SseService sseService` → `NotificationService notificationService`; 13개 `sseService.send(` → `notificationService.notify(` (~137, 153, 194, 397, 421, 426, 448, 465, 473, 492, 535, 749, 755) |
| `domain/matching/policy/assignment/MatchingPlanApplier.java:~98` | 생성자 파라미터 `SseService` → `NotificationService`; send 1개 |
| `domain/matching/policy/config/MatchingPolicyConfiguration.java:~88` | `matchingPlanApplier(...)` 빈 시그니처 갱신 |
| `domain/delivery/service/DeliveryService.java:~507` | `sendAfterCommit` 본문만 `notificationService.notify(...)`로. **AFTER_COMMIT 패턴은 손대지 않는다.** `notify` 내부 오프로딩 덕에 푸시가 느려도 요청 스레드는 커밋 직후 반환한다 |
| `domain/delivery/service/DreamiOfflineDetector.java:~93` | send 1개 |

`SseService`는 `subscribe()`(`SseController`가 씀)와 `send()`(이제 `NotificationService`만 호출)를 유지한다. `SseService.send` Javadoc에 "도메인은 `NotificationService.notify`를 쓸 것" 명시.

`SseEmitterRegistry`에 락 없는 메서드 추가:

```java
/** 지금 이 사용자에게 실시간으로 닿을 수 있는 연결이 하나라도 있는지. 오퍼 대상 선정(Phase 4)에 쓴다. */
public boolean isConnected(UUID userId) {
    Map<String, SseEmitter> connections = emitters.get(userId);
    return connections != null && !connections.isEmpty();
}
```

+ `SseService`에 위임 한 줄.

## 명시적으로 만들지 않는 것 (과잉설계)

`supports(channel)` SPI, 플러그인 레지스트리, `Notification` 빌더, 사용자별 수신 설정 컬럼, 재시도 큐. **채널 2개를 switch 하나로 다루는 게 맞는 크기다.**

---

# Phase 3 — 서비스워커 마이그레이션 (중간 리스크, 사용자 눈에 보이는 변화 없음)

현재는 `generateSW` + 소스 SW 파일 없음. 손으로 쓴 SW가 필요하니 `injectManifest`로 전환한다.

## `frontend/vite.config.ts`

```ts
VitePWA({
  strategies: 'injectManifest',
  srcDir: 'src',
  filename: 'sw.ts',          // 산출물 dist/sw.js — 배포 워크플로가 하드코딩한 경로와 일치
  registerType: 'autoUpdate',
  injectRegister: 'auto',     // registerSW.js 계속 생성 (워크플로가 이 파일도 no-cache로 올린다)
  injectManifest: { globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'] },
  includeAssets: ['favicon.svg', 'apple-touch-icon-180x180.png'],
  manifest: { /* Phase 0 반영본: maskable을 전용 파일로 */ },
  devOptions: { enabled: true, type: 'module', navigateFallback: 'index.html' },
})
```

## 반드시 손으로 재현해야 하는 4가지

`generateSW` + `registerType: 'autoUpdate'`가 조용히 넣어주던 것들이다. **하나라도 빠뜨리는 게 SW를 망가뜨리는 방법이다.**

1. **`self.__WB_MANIFEST`가 `src/sw.ts`에 문자 그대로 있어야 한다** — 없으면 빌드가 "no manifest injection point"로 실패
2. **`skipWaiting` + `clientsClaim`** — 이게 `autoUpdate`의 실체다. 없으면 새 SW가 `waiting`에 영원히 머물고 모든 탭을 닫을 때까지 사용자가 낡은 에셋에 갇힌다
3. **`cleanupOutdatedCaches()`** — 배포가 `s3 cp`를 `--delete` 없이 한다(조직 SCP가 `s3:ListBucket`을 거부). 낡은 해시 에셋이 버킷에 쌓이고, 이것 없으면 사용자 브라우저 스토리지에도 쌓인다
4. **`index.html`로의 navigation fallback + `/api/` denylist** — `generateSW`가 넣어줬다. 없으면 오프라인 상태에서 딥링크 하드 리프레시가 SPA 셸로 해석되지 않는다

## `frontend/src/sw.ts` (신규)

```ts
/// <reference lib="webworker" />
import { clientsClaim } from 'workbox-core'
import { precacheAndRoute, cleanupOutdatedCaches, createHandlerBoundToURL } from 'workbox-precaching'
import { registerRoute, NavigationRoute } from 'workbox-routing'

declare const self: ServiceWorkerGlobalScope

// ── generateSW + autoUpdate가 자동으로 넣어주던 것들을 손으로 재현한다 ──
self.addEventListener('install', () => { void self.skipWaiting() })
clientsClaim()
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()
// SPA 딥링크 오프라인 진입을 index.html로 되돌린다. /api는 절대 가로채지 않는다.
registerRoute(new NavigationRoute(createHandlerBoundToURL('index.html'), {
  denylist: [/^\/api\//],
}))

interface PushEnvelope { title: string; body: string; url: string; tag: string }

self.addEventListener('push', (event) => {
  // 페이로드는 라우팅 봉투일 뿐 — 주문 내용을 담지 않는다. 파싱 실패해도 알림은 띄운다:
  // iOS는 userVisibleOnly라 알림을 안 띄우면 구독이 해지될 수 있다.
  let data: Partial<PushEnvelope> = {}
  try { data = event.data?.json() ?? {} } catch { /* 빈 봉투 */ }

  event.waitUntil(self.registration.showNotification(data.title ?? '쉼,부름', {
    body: data.body ?? '새 알림이 도착했어요',
    icon: '/pwa-192x192.png',
    badge: '/pwa-192x192.png',
    // 같은 tag는 알림을 덮어쓴다 — 오퍼 3연타가 알림 3개로 쌓이지 않게.
    tag: data.tag ?? 'default',
    renotify: true,
    data: { url: data.url ?? '/' },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data as { url?: string } | undefined)?.url ?? '/'

  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const existing = windows.find((c) => new URL(c.url).origin === self.location.origin)
    if (existing) {
      // 이미 열려 있으면 새 창을 열지 않는다 — SPA 상태(zustand 스토어·SSE 연결)를 버리지 않기 위해서다.
      await existing.focus()
      existing.postMessage({ type: 'PUSH_NAVIGATE', url })
      return
    }
    await self.clients.openWindow(url)
  })())
})

self.addEventListener('pushsubscriptionchange', (event) => {
  // best-effort 보정. 실질적인 복구는 앱 포그라운드 시 재등록(usePushSubscription)이 담당한다.
  event.waitUntil((async () => {
    const key = (event as PushSubscriptionChangeEvent).oldSubscription?.options?.applicationServerKey
    if (!key) return
    const sub = await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
    const base = import.meta.env.VITE_API_BASE_URL ?? ''   // Vite가 SW 번들에도 인라인해준다
    await fetch(`${base}/api/v1/push/subscriptions`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(sub),
    })
  })())
})
```

## `notificationclick` — 새 창을 열지 말고 기존 클라이언트에 focus

이유:
- 이미 앱이 열린 기기에서 새 창을 열면 클라이언트 2개, `EventSource` 2개(사용자당 상한 5), 그리고 로그인이 1세션만 허용하므로 혼란스러운 split-brain 상태가 된다
- `existing.navigate(url)`이 아니라 `postMessage`인 이유: `navigate()`는 전체 문서 로드를 유발해 zustand 스토어를 날리고 SSE 재연결을 강제한다. `postMessage`면 react-router가 클라이언트 사이드 전환을 한다

### 수신 측: `frontend/src/shared/lib/push/PushNavigationBridge.tsx` (신규)

`useNavigate`가 필요하므로 `main.tsx`에 `SseStatusBanner` 옆에 마운트하는 작은 컴포넌트다. `navigator.serviceWorker.addEventListener('message', ...)`를 듣고 `PUSH_NAVIGATE`에서:

1. `navigate(url)`
2. **`useMatchingStore.getState().syncCurrentMatching()`** ← wake-up 설계의 전부다
3. 스냅샷이 비어 있으면(앱을 여는 동안 오퍼가 만료됨) 토스트: `show({ icon:'bell', title:'요청이 이미 마감됐어요', dedupeKey:'offer-expired' })`. 이게 없으면 사용자는 부르르 울린 뒤 앱을 열었는데 아무것도 없는 최악의 경험을 한다. `dedupeKey`가 3초 재연결 폴링 루프에서 반복 발화를 막는다

추가로 **`visibilitychange` 리스너**를 함께 둔다 — 문서가 visible이 될 때 `syncCurrentMatching()`. 백그라운드였지만 열려 있던 탭은 `SseProvider`가 리마운트되지 않으므로 기존 `connected` 훅이 발화하지 않는다.

백엔드가 봉투에 넣는 `url` 값: `offer_popup`/`dreami_info` → `/matching`, `delivery_started_dreami` → `/delivery-track?orderId=…`, 부르미 배달 이벤트 → `/delivery-detail?orderId=…`. **`shared/config/routes.ts`의 값에서 가져와** 드리프트를 막는다.

## 놓치기 쉬운 TypeScript 설정

`pnpm build`가 `tsc -b`를 돌리고 `tsconfig.app.json`은 `include: ["src"]` + `lib: ["ES2023","DOM"]`이다. `src/sw.ts`를 그냥 넣으면 모든 `ServiceWorkerGlobalScope` 멤버에서 실패한다.

1. `tsconfig.app.json`에 `"exclude": ["src/sw.ts"]` 추가
2. 신규 `frontend/tsconfig.worker.json` — `"lib": ["ES2023","WebWorker"]`, `"types": ["vite-plugin-pwa/client"]`, `"include": ["src/sw.ts"]`, `noEmit`, `tsconfig.app.json`과 같은 bundler 모드 블록
3. `tsconfig.json`의 `references`에 `{ "path": "./tsconfig.worker.json" }` 추가
4. 새 devDeps: `workbox-core`, `workbox-precaching`, `workbox-routing` — **`vite-plugin-pwa@1.3.0`이 기대하는 workbox 버전에 맞출 것**

## 배포

`.github/workflows/frontend-deploy.yml`은 **변경 불필요** — `dist/sw.js`, `dist/registerSW.js`, `dist/manifest.webmanifest`가 같은 이름으로 계속 생성되고 이미 `no-cache,no-store,must-revalidate` + CloudFront `/*` 무효화를 받는다. **다만 머지 전에 `pnpm build && ls dist/`로 파일명을 직접 확인할 것.** 여기서 파일명이 바뀌면 이미 설치된 사용자에게 조용하고 복구 어려운 장애가 된다.

> ⚠️ **이 플랜에서 가장 위험한 배포다.** 망가진 SW는 이미 설치된 사용자에게 전달되고 모든 탭이 닫힐 때까지 남는다(롤백보다 오래 살아남는다).
>
> **완화: 푸시 핸들러 없이 먼저 배포한다.** SW 마이그레이션 → 캐싱·갱신 동작을 단독으로 검증 → 그다음 커밋에서 `push`/`notificationclick`/`pushsubscriptionchange` 추가.

---

# Phase 4 — 매칭 유령 필터 (3줄, 최고 가치, 푸시와 완전 독립)

> 엄밀히는 알림 기능이 아니라 이 조사 중에 발견된 매칭 품질 문제다. Phase 2의 `isReachableNow`만 필요하고 푸시와 완전히 독립적이며, 사용 가능한 매칭 개선 중 가장 크다.

## 지금의 실제 피해

`attemptOfferRound`가 가장 가까운 `MATCHING` 드리미 3명을 **liveness 확인 없이** 뽑아 `PROPOSED`로 표시하고 30초 `ExpireDreamiOffer`를 스케줄한다. `goOffline`을 호출하지 않고 브라우저가 죽은 드리미(모바일에서 흔하다 — 앱 스와이프, 메모리 압박으로 탭 종료)는 여전히 `dreamiMap`에 `MATCHING` 상태로 남아 있다. 결과:

- 오퍼 슬롯 3개 중 최대 3개가 **응답이 물리적으로 불가능한** 사용자에게 간다 (각각 `sse.events.dropped{reason=not_connected}` 발생 — **이 지표가 이미 이 일이 벌어지고 있음을 증명한다**)
- 주문이 30초를 태우고 `closeForRematch()` → `WAITING` → 다음 배치 윈도 대기
- 더 나쁜 것: 그다음 `OutcomeCooldownOfferPolicy`가 `matching.cooldown.dreami-expiration=10m`을 그 유령들에게 적용한다. 유령은 스스로 정리되지만 **실제 주문 하나가 라운드 전체를 낭비한 뒤에** 그렇게 된다

## 푸시는 이 계산을 바꾸지 않는다

푸시로 닿을 수 있지만 SSE가 끊긴 드리미가 30초 안에 푸시 수신 → 잠금해제 → 앱 열기 → 로딩 → 수락을 다 하는 일은 사실상 없다. **따라서 게이트는 "살아있는 SSE 연결"이어야 하고 "푸시 구독이 있음"이 아니다.**

## 구현 — 엔진 스레드 위 `ConcurrentHashMap.get` 2개 (나노초, 블로킹 없음)

```java
// MatchingService.applyRunMatchingAssignmentCycle() — 배치 경로
MatchingAssignmentProblem problem = matchingAssignmentProblemAssembler.assemble(
        orderOfferGroups(), reachableWaitingDreamis());

/** SSE 연결이 살아 있는 대기 드리미만. 30초 안에 응답할 수 없는 유령에게 오퍼 슬롯을 낭비하지 않는다. */
private List<WaitingDreami> reachableWaitingDreamis() {
    return waitingDreamis().stream()
            .filter(dreami -> notificationService.isReachableNow(dreami.dreamiId()))
            .toList();
}
```

```java
// MatchingService.attemptOfferRound() — fallback 스캔 경로, 기존 candidates 스트림에 한 줄 추가
.filter(dreami -> notificationService.isReachableNow(dreami.dreamiId()))
```

## `MatchingEligibilityPolicy`에 liveness를 넣지 말 것

그 계약 Javadoc이 **명시적으로 금지한다**: "시스템 시각을 직접 조회하지 않는다 / 같은 candidate와 evaluatedAt이 주어지면 항상 같은 결과를 반환한다(결정적이어야 한다)".

SSE liveness는 외부 가변 상태다. 게다가 `MatchingPlanValidator.validate`가 plan에 대해 eligibility를 **재실행**하므로, 비결정적 정책은 할당 정책이 방금 만든 plan을 거부할 수 있다. `assemble()` 입력에서 걸러야 problem·policy·validator가 모두 일관된다(걸러진 드리미는 problem에 아예 등장하지 않는다).

## 계측·테스트

- 지표: `matching.candidates.filtered{reason=not_connected}` 카운터 — 효과를 기존 Grafana에서 증명
- 테스트: `SSE_연결이_없는_드리미는_오퍼_후보에서_제외된다`

## 범위 외

마지막 SSE 연결이 끊길 때 `dreamiMap`에서 드리미를 자동 제거하는 것. 더 깔끔해 보이지만, 모바일 `EventSource`는 백그라운드·네트워크 핸드오버로 끊임없이 재연결하므로 유예기간(~60초)과 재연결 시 취소 경로가 필요해진다 — 진짜 복잡도다. 필터가 이득의 전부를 이미 가져가고, liveness 판정이 순간적으로 틀려도 우아하게 열화된다. 필터가 불충분하다고 증명되면 그때 재검토.

---

# Phase 5a — 웹푸시 백엔드 (`web-push.enabled=false`로 다크 머지)

## VAPID 키

```properties
# application.properties
web-push.enabled=${WEB_PUSH_ENABLED:false}
web-push.public-key=${WEB_PUSH_PUBLIC_KEY:}
web-push.private-key=${WEB_PUSH_PRIVATE_KEY:}
web-push.subject=${WEB_PUSH_SUBJECT:mailto:noreply@example.com}
```

키 쌍은 밖에서 한 번 생성한다: `npx web-push generate-vapid-keys` (P-256).

`WebPushProperties`는 `record` + `@ConfigurationProperties(prefix="web-push")`, `QuickApplication`의 `@EnableConfigurationProperties` 목록에 등록 — `SolapiProperties`와 동일 패턴. `WebPushSender`는 `@ConditionalOnProperty(name="web-push.enabled", havingValue="true")`.

**SMS와 달리 dev double을 만들지 않는다** — 비활성이면 `ObjectProvider<WebPushSender>`가 비어 있고 `notify`가 그 채널을 건너뛴다. 로그만 찍는 `DevWebPushSender`는 순수 의식(ceremony)이다.

### 공개키를 `VITE_*`로 넘기지 않는다

Vite는 `VITE_*`를 빌드 타임에 인라인하므로(§0 참조), 그렇게 하면 키 교체마다 전체 리빌드 + CloudFront 무효화 + `frontend-deploy.yml`에 네 번째 secret이 필요해진다. 대신 엔드포인트로 제공:

```java
@Operation(summary = "Web Push VAPID 공개키 조회",
        description = "브라우저가 pushManager.subscribe의 applicationServerKey로 쓰는 공개키. "
                + "공개 상수이므로 로그인 없이 조회할 수 있다. 미설정(push 비활성)이면 publicKey는 null이다.")
@GetMapping("/vapid-public-key")
@PublicApi
public VapidPublicKeyDto vapidPublicKey() { ... }
```

응답은 envelope로 감싸지므로 프론트는 `.result.publicKey`를 읽는다. **`publicKey`가 `null`이면 프론트는 "서버에서 푸시 꺼짐"으로 읽고 권한 카드를 렌더하지 않는다** → 푸시 on/off가 프론트 배포 없는 백엔드 env 플립이 된다.

## Java 라이브러리 선택

| 후보 | 판정 |
|---|---|
| **`nl.martijndwars:web-push:5.1.1`** | **권장.** VAPID JWT(ES256) + aes128gcm 페이로드 암호화 + TTL/Urgency 헤더를 처리한다. 단점: ~2022년 이후 미유지보수, BouncyCastle 필요(`bcprov-jdk18on`, `bcpkix-jdk18on`), `org.asynchttpclient` 전이 의존, `send()`가 checked exception을 다발로 던져 `BusinessException(NotificationErrorCode.PUSH_SEND_FAILED)`로 래핑해야 함. **마감 앞에서 결정적인 장점: 예제와 한국어 자료가 가장 많아 디버깅이 가장 빠르다** |
| `com.interaso:webpush` | 더 가볍고 사실 더 좋다 — JDK `HttpClient` + JDK EC 암호, **BouncyCastle 불필요**, Kotlin stdlib 외 전이 의존 없음. 팀에 이걸 처음 써볼 사람이 있으면 이쪽을 택하라. 기본 권장으로 두지 않는 유일한 이유는 마감 근처에서 낯선 라이브러리 디버깅이 잘못된 리스크이기 때문 |
| 손으로 구현 | **거부.** VAPID JWT만이면 JDK `Signature.getInstance("SHA256withECDSA")`로 ~40줄이고 괜찮다. 그런데 aes128gcm은 ECDH + HKDF + AES-GCM에 정확한 salt/nonce/padding 레이아웃을 요구한다. 미묘하게 틀리면 **디버깅이 거의 불가능한 조용한 미전달**이 된다 |
| 페이로드 없는 푸시(VAPID JWT만, 빈 본문) | 매력적이다 — 라이브러리도 BouncyCastle도 없고 wake-up 설계와도 맞는다. **그래도 거부:** iOS가 `userVisibleOnly: true`를 강제하므로 SW가 알림을 **반드시** 띄워야 하는데, 페이로드가 없으면 무슨 문구를 띄울지 알 수 없다. 우회책(SW가 API를 fetch해 문구 결정)은 `SameSite=None` 쿠키를 실은 크로스오리진 SW fetch를 요구하고, 이건 Chrome에서 되고 iOS에서 죽는 종류의 것이다 |

```gradle
// backend/build.gradle
implementation 'nl.martijndwars:web-push:5.1.1'
implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
implementation 'org.bouncycastle:bcpkix-jdk18on:1.78.1'
```

`WebPushSender.init()`의 `@PostConstruct`에서 `Security.addProvider(new BouncyCastleProvider())` — `SolapiSmsSender.init()`과 같은 모양.

## `PUSH_SUBSCRIPTION` DDL

`backend/sql/sym-boorm-ddl.sql`에 추가 (기존 파일의 "제약은 별도 `ALTER TABLE` 문" 스타일 준수):

```sql
CREATE TABLE `PUSH_SUBSCRIPTION` (
    `push_subscription_id` binary(16)   NOT NULL,
    `boormi_id`            binary(16)   NOT NULL,
    `endpoint`             varchar(512) NOT NULL COMMENT 'push 서비스 엔드포인트 URL. 브라우저·기기·SW 조합마다 유일하므로 이것이 기기 식별자다(unique)',
    `p256dh`               varchar(255) NOT NULL COMMENT '클라이언트 공개키(base64url)',
    `auth`                 varchar(255) NOT NULL COMMENT '클라이언트 인증 시크릿(base64url)',
    `user_agent`           varchar(255) NULL     COMMENT '디버깅용 기기 식별 문구',
    `created_dtm`          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_success_dtm`     timestamp    NULL     COMMENT '마지막 전송 성공 시각',
    `consecutive_failures` int          NOT NULL DEFAULT 0 COMMENT '연속 5xx 실패 횟수. 10회 도달 시 정리 대상'
);

ALTER TABLE `PUSH_SUBSCRIPTION` ADD CONSTRAINT `PK_PUSH_SUBSCRIPTION` PRIMARY KEY (`push_subscription_id`);
ALTER TABLE `PUSH_SUBSCRIPTION` ADD CONSTRAINT `UQ_PUSH_SUBSCRIPTION_ENDPOINT` UNIQUE (`endpoint`);
CREATE INDEX `IX_PUSH_SUBSCRIPTION_BOORMI` ON `PUSH_SUBSCRIPTION` (`boormi_id`);
```

선택의 근거:
- `varchar(512)` + unique 인덱스는 안전하다 — utf8mb4로 2048바이트, InnoDB 3072바이트 키 한계 아래. **768자를 넘기면 해시 컬럼이 필요해진다**
- `BOORMI`로의 FK 없음 — DDL 파일이 테이블마다 FK를 명시적으로 추가하는데 기존 스타일이 일관되지 않다. 인덱스만 두면 된다(`findAllByBoormiId`가 모든 푸시마다 돈다)
- `user_id`가 아니라 `boormi_id` — 스키마 전체가 이걸 계정 키로 쓰고 `boormiId == dreamiId`
- **unique 키가 `(boormi_id, endpoint)`가 아니라 `endpoint`인 것이 미묘한 핵심이다.** 공용 기기에서 같은 endpoint가 다른 계정으로 옮겨갈 수 있다. upsert가 두 번째 행을 넣는 게 아니라 `boormi_id`를 **재배정**해야 한다 — 아니면 이전 사용자가 새 사용자의 푸시를 계속 받는다

운영이 `ddl-auto=none`이므로 파일 하단에 기존 `-- ALTER TABLE DELIVERY ADD COLUMN ...` 주석과 같은 형식으로 **복사·실행 가능한 블록도 추가**한다.

엔티티는 §5 컨벤션 그대로: `@Entity @Table(name="PUSH_SUBSCRIPTION")`, `@Getter`, `@NoArgsConstructor(access=PROTECTED)`, 정적 팩토리 `PushSubscription.create(UUID boormiId, String endpoint, String p256dh, String auth, String userAgent)`, PK `@JdbcTypeCode(SqlTypes.BINARY) @Column(columnDefinition="BINARY(16)")`, 뮤테이터 `reassignTo(UUID)`, `markSuccess()`, `markFailure()`.

## 엔드포인트

```
GET    /api/v1/push/vapid-public-key   @PublicApi   → { publicKey }
POST   /api/v1/push/subscriptions      (로그인 필수) body { endpoint, keys: { p256dh, auth } } → 204, 멱등 upsert
DELETE /api/v1/push/subscriptions      (로그인 필수) body { endpoint } → 204, 멱등
```

요청 DTO 모양을 브라우저 `PushSubscription.toJSON()`과 일치시켜 프론트가 `JSON.stringify(subscription)`을 매핑 없이 그대로 보낼 수 있게 한다 — 중첩 `keys` 객체가 그 값을 한다.

현재 사용자 식별은 기존 방식 그대로: `@LoginUser UUID boormiId`.

`PushSubscriptionService.subscribe` (단일 `@Transactional`):

```java
repository.findByEndpoint(request.endpoint())
    .ifPresentOrElse(
        existing -> existing.refresh(boormiId, keys),   // 소유자 재배정 + 키 갱신 + 실패 카운터 초기화
        ()       -> repository.save(PushSubscription.create(boormiId, ...)));
```

## 기기 식별

**`endpoint`가 곧 기기 식별자다** — 명세상 (브라우저, 기기, SW 등록) 조합마다 유일하다. 별도 `DEVICE` 테이블도, 클라이언트 생성 device UUID도, `device_id` 컬럼도 필요 없다. 논리 키는 `(boormi_id, endpoint)`이고 소유권 이전이 가능하도록 `endpoint`에 전역 unique를 둔다. 사용자에게 보내기 = `findAllByBoormiId(userId)` → 루프 → 전송 → 실패분 정리.

## 생명주기

| 사건 | 동작 |
|---|---|
| **로그아웃** | 프론트가 `api.logout()` **전에** `DELETE /push/subscriptions` 호출(로그아웃 후엔 세션이 없다). 서버는 행 삭제. 브라우저 구독은 의도적으로 **유지**한다(`unsubscribe()` 호출 안 함) — 권한이 남으니 재로그인 시 두 번째 프롬프트 없이 조용히 재등록된다. 전달을 게이트하는 건 DB 행이므로 공용 기기도 안전하다 |
| **`REPLACED_BY_LOGIN`** | **푸시 구독은 건드리지 않는다.** `disconnectAll`을 따라 다른 기기를 정리하고 싶어지지만, 사용자는 정당하게 폰 + 데스크톱 브라우저를 쓴다. 1세션 제약은 SSE/세션 제약이지 계정 제약이 아니다. 정리하면 지금 안 쓰는 기기의 푸시가 조용히 깨진다. **알려진 거친 부분:** 세션이 교체된 기기에 푸시가 도착해 탭하면 로그인 화면이 뜬다. 수용하고 Javadoc에 명시 |
| **404 / 410** | 구독 영구 소멸(설치 삭제, 사이트 데이터 초기화). 즉시 `repository.deleteByEndpoint(endpoint)`, `push.subscriptions.pruned` 증가. **가장 중요한 정리 경로** — 없으면 죽은 행이 쌓이고 매 전송이 HTTP 왕복을 낭비한다 |
| **429** | 레이트 리밋. 건너뛰고 로그. **삭제 금지, 재시도 금지**(재시도가 도착할 때쯤 알림은 이미 낡았다) |
| **5xx / timeout** | `markFailure()`. `consecutive_failures >= 10`이면 삭제. 성공 시 `markSuccess()`(카운터 리셋 + `last_success_dtm` 갱신) |
| **413** | 페이로드 과대 — 우리 봉투 크기로는 불가능하므로 `error`로 로그(버그라는 뜻) |
| **`pushsubscriptionchange`** | **best-effort로만 취급.** Chrome은 드물게 쏘고, Firefox는 사실상 안 쏘고, SW의 크로스오리진 쿠키 fetch가 실패할 수 있다. **실제 복구 메커니즘은 앱 포그라운드 재검증** — `usePushSubscription` 훅이 마운트/visible 복귀마다 현재 구독을 재POST한다. 싸고, 세션이 보장되고, 조용히 회전한 endpoint까지 모든 드리프트를 자가 치유한다. **포그라운드 재검증을 먼저 만들고 SW 핸들러는 덤으로** |

---

# Phase 5b — 웹푸시 프론트 UX (Phase 0 + Phase 3 선행 필요)

## 새 디렉터리 `frontend/src/shared/lib/push/`

```
pushCapability.ts        # 순수 판별 함수 (부수효과 없음)
usePushSubscription.ts   # 구독 상태 + enable() + 포그라운드 재등록
PushEnableCard.tsx       # 제스처 표면 (허용하기 버튼)
PushGuidanceCard.tsx     # 설치·브라우저 안내
PushNavigationBridge.tsx # (Phase 3에서 생성)
```

`pushCapability.ts`:
- `isInAppBrowser()` — UA에 `KAKAOTALK|Instagram|FBAN|FBAV|NAVER|Line|DaumApps`
- `isStandalone()` — `window.matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone === true`
- `isIos()`
- `supportsPush()` — `'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window`

`usePushSubscription()`은 판별된 상태를 반환해 UI가 중첩 조건문 없이 switch 하나로 끝나게 한다:

| 상태 | UI |
|---|---|
| `'in-app-browser'` | 외부 브라우저로 열기 안내 |
| `'needs-install'` | iOS + 미설치 → 홈 화면에 추가 안내 |
| `'installable'` | Android + 미설치 → `beforeinstallprompt`로 설치 버튼 |
| `'unsupported'` | **안내 없이 조용히 아무것도 렌더하지 않는다** |
| `'default'` | `PushEnableCard` (허용하기) |
| `'granted'` | 렌더 없음. 포그라운드마다 구독 재등록만 |
| `'denied'` | 기기 설정 안내 (재요청 불가) |

## iOS 제약 (전부 하드 제약)

- 웹푸시는 **홈 화면 설치**가 필수(iOS 16.4+). "권장"이 아니라 Safari 탭에는 `PushManager`가 **문자 그대로 없다**
- `Notification.requestPermission()`은 **사용자 제스처 안에서만**. 로드 시 자동 요청 불가
- `userVisibleOnly: true` 강제
- **인앱브라우저 지원 0.** 카카오톡 인앱브라우저는 한국에서 링크 공유 서비스의 가장 흔한 진입점이고, SW 푸시도 홈화면 추가도 없다
- **거절은 최종적이다.** `Notification.permission === 'denied'`는 페이지에서 재요청할 수 없고 iOS 설정에서만 되돌린다

## 권한을 묻는 위치 — 콜드 로드에서 절대 묻지 않는다

콜드 로드에 묻는 것은 **되돌릴 수 없는 거절을 수집하는 방법**이다. 동기가 최고조인 실제 클릭 순간에 묻는다:

- **드리미**: `MatchingScreen`에서 `goOnline()` 성공 직후. 사용자가 방금 "시작하기"를 눌렀고 의도가 문자 그대로 "콜 알려줘"다.
  문구: **"화면을 닫아도 놓친 콜을 알려드릴까요?"** (§4의 문구 규율)
- **부르미**: 콜 등록 성공 후 매칭 대기 화면 진입 시.
  문구: "드리미를 찾으면 바로 알려드릴까요?"

두 곳 다 이미 클릭 핸들러 컨텍스트라 `enable()`이 제스처 안에서 실행된다.

`enable()`: `Notification.requestPermission()` → `registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(publicKey) })` → `POST /api/v1/push/subscriptions`. 성공/거절 토스트는 `enable()`이 띄운다(각 호출처가 문구를 발명하지 않게).

거절/닫기는 `localStorage`의 `push-prompt-dismissed-at`에 기록해 **7일 안엔 다시 묻지 않는다.** 조르지 말 것.

## 폴백 경로

1. **인앱브라우저(카카오)** → `PushGuidanceCard`에 "Safari에서 열기 / Chrome에서 열기" + 링크 복사 버튼. Android에서는 카카오 브라우저가 "다른 브라우저로 열기" 메뉴를 노출하고, iOS에서는 사용자가 링크를 복사해야 한다. **이 카드는 장식이 아니라 하중을 받는 인프라다** — 카카오톡으로 유입된 사용자에게 tier 2가 존재할 수 있는 유일한 경로다
2. **iOS + 미설치** → 홈 화면 추가 3단계 일러스트(공유 → 홈 화면에 추가 → 추가). 자동화도 프롬프트도 불가능하다. **정적 일러스트 카드가 사용 가능한 기술의 전부다**
3. **Android + 미설치** → `beforeinstallprompt` 캡처 후 "앱으로 설치" 버튼. **Phase 0 아이콘 없이는 동작하지 않는다**
4. **denied** → "기기 설정 → 알림에서 쉼,부름을 허용해주세요." 이 사용자는 인앱 tier + SMS 안전망으로 떨어진다

## 마무리

Phase 5a 이후 **`pnpm api:gen`** 재생성 필요. `pnpm lint && pnpm build` 통과.

---

# Phase 6 — SMS tier (트리거 1개, `SMS_FALLBACK_ENABLED=false` 기본)

## 설계 결정: SMS는 `NotificationChannel`이 아니다

트리거가 "배달 중 드리미 장시간 무소식" **하나뿐**이므로 SMS를 파사드의 채널로 만들 이유가 없다. `DreamiOfflineDetector` → `SmsFallbackNotifier` → 기존 `SmsSender` 직접 경로로 간다.

이건 단순화이면서 **정책 표 기본값보다 강한 안전장치**다: 어떤 이벤트도 실수로 유료 채널에 배선될 **경로 자체가 존재하지 않는다.**

> **왜 이게 중요한가:** `offer_popup`을 SMS에 잘못 연결하면 오퍼 3개/라운드 × 약 3라운드 × 1,000주문/일 ≈ **9,000건/일 ≈ 18만원/일**이다. 이 tier의 리스크 표면 전체가 "잘못 배선된 정책 항목 하나"다.

## 트리거 정의 — 영속 상태로만 판정 (재시작에 견딤)

- **상태:** `DELIVERY.delivery_cd ∈ TRACKED_STATUSES` (`PICKUP_NORMAL`, `PICKUP_DELAYED`, `DELIVERING`) **AND** `last_location_dtm < now - 3분` **AND** `offline_sms_sent_dtm IS NULL`
- **임계값 3분(180초)** vs 기존 SSE 배너 임계값 30초.
  근거: 드리미가 5초마다 GPS를 보내므로 30초(연속 6회 누락)는 엘리베이터·터널을 올바르게 무시하고 부르미용 배너에 적합하다. **180초(연속 36회 누락)는 앱이 죽었거나 폰 배터리가 끝났거나 라이더가 이탈한 상태** — 배너로 고칠 수 없고 문자로는 고칠 수 있는 상태다
- **측정 수단:** `DELIVERY.last_location_dtm` — §2에 확인된 대로 이미 영속되고 `DeliveryRepository.findStaleLocationDeliveries(statuses, threshold)`가 이미 조회한다.
  **핵심 설계 판단: 연결 업타임 트래커를 만들지 않는다.** GPS 하트비트가 "이 라이더의 앱이 죽었다"의 더 좋고 이미 내구성 있는 프록시이고, 재사용하면 새 상태가 0이다
- **구현:** `DreamiOfflineDetector`의 기존 5초 스캔(`:64`)에 두 번째 높은 임계값을 추가. **이미 정확히 맞는 행들을 로드하고 있다**

## SMS를 절대 트리거하지 않는 이벤트 (재논의 방지)

| 이벤트 | 왜 안 되는가 |
|---|---|
| `offer_popup` | **이 문서에서 가장 중요한 가드레일.** 30초 TTL vs 수 초 SMS 지연 → 구조적으로 무용하고, 비용이 재앙적이다(위 계산) |
| `dreami_info` | 같은 30초 TTL |
| `delivery_location` | 초당 이벤트 |
| `offer_closed`, `boormi_rejected`, `offer_error` | 앱이 열려 있을 때만 의미 있음 |
| `delivery_dreami_offline` (→부르미) | 부르미는 화면을 보고 있다. 아니라면, 손쓸 수 없는 지연에 대한 문자는 소음이다 |
| `delivery_started_*`, `delivery_delivering` | 인앱 + 푸시로 충분. 긴급하지 않다 |
| `delivery_cancelled` | 환불(돈)이 걸려 정당화될 여지가 있지만 **이번 범위에서는 제외.** 인앱 + 푸시로 커버 |
| `delivery_completed` | 비대면 배달("문 앞에 두었습니다")에 그럴듯하지만 **일단 제외.** 볼륨이 주문 수만큼 늘어 비용이 선형 증가한다. 측정 후 재검토할 가장 유력한 후보 |

## 재시작 견디는 멱등성 — 테이블이 아니라 컬럼 하나

`DreamiOfflineDetector.notifiedOrders`(`:50`)는 `ConcurrentHashMap.newKeySet()`으로 **재시작 시 소실된다.** SSE엔 무해하지만 유료 SMS엔 용납 불가 — 정체된 배달 중에 배포하면 재발송된다.

```sql
-- backend/sql/sym-boorm-ddl.sql 의 DELIVERY 정의에 컬럼 추가 + 아래 ALTER 주석도 함께
-- ALTER TABLE `DELIVERY` ADD COLUMN `offline_sms_sent_dtm` timestamp NULL
--   COMMENT '드리미 장시간 무소식 안내 문자 발송 시각(재발송 방지). 위치 수신이 재개되면 NULL로 되돌린다';
```

- **중복 제거:** 쿼리가 `offline_sms_sent_dtm IS NULL`로 필터하므로 **구조적으로** 재시작에 견딘다
- **재장전:** `last_location_dtm`을 찍는 위치 갱신 경로(`Delivery.java:89` 부근)에서 이 컬럼을 `null`로 되돌려, 같은 배달의 **두 번째** 진짜 장애도 알람할 수 있게 한다. 기존 `notifiedOrders.retainAll(staleOrderIds)`(`:86`) 자가치유 트릭을 영속화한 것

**내구성 있는 중복 제거의 총비용: nullable timestamp 컬럼 하나.**

## 레이트 리밋 — 새로 만들지 않는다 (YAGNI)

**`SmsSendRateLimiter` 인스턴스를 공유하지 말 것.** 그 윈도(`verification.phone-max=5/24h`, `verification.global-max=1000/24h`)는 인증번호용이다. per-phone 버킷을 공유하면 **알림 문자를 받은 라이더가 번호 재인증에서 잠기는 진짜 계정복구 버그**가 된다.

새 리미터도 필요 없다: 주문별 영속 중복제거가 있으면 **도메인 상태 자체가 레이트 리밋**이다 — 배달당 최대 1건. `@Scheduled`가 5초마다 재발송하는 유일한 무한 루프를 컬럼이 닫는다.

*(팀이 불안해하면 전역 일일 비용 백스톱만 추가: `SmsSendRateLimiter`가 4개 윈도 값을 `VerificationProperties`에서 직접 읽는 대신 생성자 파라미터로 받게 고치고(~10줄) `@Qualifier` 빈 2개로 분리 — `verificationSmsRateLimiter`, `notificationSmsRateLimiter`(`notification.sms.global-max=200/24h`, per-phone 윈도 없음). 정확성엔 불필요하다.)*

## 문구

SOLAPI 단문 SMS는 ≤90바이트, 한글은 관련 인코딩에서 ~2바이트/자 → 실질 예산은 **`[쉼,부름]` 접두어 포함 ~45자**. 넘으면 **조용히 LMS로 승격되어 요금이 약 3배**가 된다.

**URL을 넣지 않는다** — 바이트 예산을 먹고, 미등록 발신자의 링크 문자를 한국 통신사가 스팸으로 취급한다.

```
[쉼,부름] 진행 중인 배달이 멈췄어요. 앱을 다시 열어 배달을 이어가 주세요.
```

정보성(transactional) 메시지라 `(광고)` 접두어나 수신거부 문구가 필요 없고, 그래서 바이트 예산이 달성 가능하다. `SmsFallbackNotifier`의 상수로 두고 `SmsVerificationService:~36`의 기존 포맷과 같은 방식으로 조립.

`SmsSender`(`domain/user/sms/`)를 **변경 없이 재사용** — `DevSmsSender`가 `solapi.enabled=false`에서 이미 로그만 찍으므로 로컬 개발은 dev double을 무료로 얻는다. `global/notification` → `domain/user/sms` 패키지 간 의존이 생기는데, 인프라 재사용으로 수용한다(`SmsSender` 인터페이스를 `global/sms/`로 옮기는 것보다 싸다).

## 킬 스위치

```properties
notification.sms-fallback-enabled=${SMS_FALLBACK_ENABLED:false}
notification.dreami-offline-sms-threshold=3m
```

**실제 전화번호로 수동 E2E 테스트를 통과한 뒤에만 켠다.**

## 비용

- SOLAPI 단문 SMS ≈ **20원/건**
- 1,000주문/일에서 5%가 트리거를 밟으면 ~50건/일 ≈ **1,000원/일 (~3만원/월)**. 무시할 수준
- **재앙 시나리오는 볼륨 증가가 아니라 잘못 배선된 이벤트 하나다.** 이를 막는 메커니즘은 (a) SMS가 채널이 아니라는 구조적 결정 (b) `NotificationPolicy`의 `IN_APP` 기본값 (c) `delivery_location`/`offer_popup` 회귀 테스트 (d) 영속 중복제거 컬럼 (e) 킬 스위치 프로퍼티

---

## §6 지표 (`NOTIFICATION_LOG` 테이블은 만들지 않는다)

`SseEmitterRegistry`가 이미 쓰는 Micrometer → Prometheus → Grafana(`monitoring/`, `management.endpoints.web.exposure.include=health,info,prometheus`) 경로에 **같은 스타일로** 추가:

- `notification.sent{channel, event}`
- `notification.dropped{channel, reason}` — `not_connected`, `no_subscription`, `send_failed`, `policy_disabled`
- `push.subscriptions.pruned{reason}` — `gone_410`, `too_many_failures`
- `sms.sent{trigger}`
- `matching.candidates.filtered{reason=not_connected}` (Phase 4)
- `notification-outbound` 큐 깊이 Gauge

### 감사 테이블을 만들지 않는 근거

- **중복 제거** — 가장 강한 명분인데, `DELIVERY`의 nullable timestamp 컬럼 **하나**가 완전히 해결한다. 컬럼 하나가 담는 것을 테이블로 담는 건 과잉설계의 정의다
- **"푸시가 되고 있나"** — Micrometer가 답한다. 이미 배선돼 있고 `SseEmitterRegistry`가 이미 이렇게 쓴다
- **사용자별 포렌식** ("드리미 X가 14:32에 왜 콜을 못 받았나") — 유일한 진짜 갭이다. `NotificationService`의 non-in-app 전송당 구조화 `log.info`(`userId`, `event`, `channel`, `result`) 한 줄로 충분하다. 단일 인스턴스 배포라 grep 가능하고 `logging.level.com.naengsam.quick`이 이미 env 조절 가능하다
- **SMS 요금 분쟁** — SOLAPI 콘솔이 권위 있는 기록이다. 로컬 복제는 아무것도 사주지 않는다

테이블을 만드는 비용: 테이블 + 엔티티 + 리포지토리 + 모든 알림마다 쓰기 — fire-and-forget SSE 전송이 DB 쓰기가 되어 **뜨거운 `delivery_location` 경로에 실제 지연·실패 모드가 생긴다** + 보존/정리 스토리.

*팀이 나중에 꼭 원한다면 최소 형태: `NOTIFICATION_LOG(notification_log_id binary(16) PK, boormi_id binary(16), channel varchar(20), event_name varchar(50), dedup_key varchar(100) UNIQUE, result varchar(20), sent_dtm timestamp, INDEX(boormi_id, sent_dtm))`. 값을 하는 부분은 `UNIQUE(dedup_key)`뿐이고 그건 nullable 컬럼이 이미 제공한다. `WEB_PUSH`와 `SMS`만 기록하고 `IN_APP`은 절대 기록하지 말 것.*

---

## §7 잘라내는 것 (재논의 방지용 목록)

| 항목 | 이유 |
|---|---|
| **카카오톡 알림톡** | 기술적으로는 거의 드롭인이다 — 같은 `com.solapi:sdk:1.0.3`의 `DefaultMessageService`에 `KakaoOption`을 붙이면 되고 수신 불가 시 SMS 자동 폴백까지 되며 6.5~9원 vs 20원으로 더 싸다. **막는 것은 기술이 아니라 조직이다:** 카카오 비즈니스 채널 개설 + 채널 검수, SOLAPI 발신프로필 등록, **템플릿 사전 심사 영업일 1~3일**(반려되면 시계가 다시 돈다). 데모 날짜가 고정된 팀이 하루 1,000원짜리 문자 두 개를 위해 1~3영업일 외부 승인에 일정을 거는 건 나쁜 거래다. SDK는 이미 의존성에 있으니 **마감 후 업그레이드**로 문서화: 채널이 생기면 같은 `SmsSender` 인터페이스 뒤에서 `KakaoOption` + 템플릿 코드 변경뿐이다 |
| `NOTIFICATION_LOG` | §6 |
| 채널 sender SPI / 플러그인 레지스트리 / `Notification` 빌더 | 채널 2개엔 과잉 |
| 사용자별 수신 설정 컬럼 | 아무도 요청하지 않았고 설정 화면을 함의한다 |
| 별도 `DEVICE` 테이블 | `endpoint`가 이미 기기 식별자다 |
| 푸시 재시도 큐 | 재시도된 알림은 낡은 알림이다 |
| 멀티 인스턴스 조정 | 단일 JVM은 `SseEmitterRegistry`·`MatchingService`·`ActiveSessionRegistry`·`VerificationCodeStore`·`SmsSendRateLimiter`·`DreamiOfflineDetector`가 **이미 전제**하고 `docs/donghyeok/sse/multi-server-scaling.md`에 문서화돼 있다 |
| SSE 손실 시 `dreamiMap`에서 드리미 자동 제거 | Phase 4 범위 외 참조 |
| `matchingStore.message` → `toastStore` | Phase 1 범위 외 참조 |
| 비대면 배달완료 SMS | Phase 6 표 참조. 측정 후 재검토 |

---

## §8 의존 그래프 · 리스크

```
Phase 0 (아이콘) ─────────────┐
Phase 3 (SW 마이그레이션) ────┼──→ Phase 5b (푸시 프론트)
Phase 2 (파사드) ──→ Phase 5a (푸시 백엔드) ──┘
        └─────────→ Phase 4 (매칭 필터)
Phase 6 (SMS) ── Phase 2와 무관, DreamiOfflineDetector 단독
Phase 1 (토스트) ── 완전 독립
```

**Phase 0 / 1 / 2 / 6은 파일도 레이어도 겹치지 않아 동시 착수 가능.** Phase 4는 2 직후.

Phase별 성격:

| Phase | 비용 | 리스크 | 사용자 가시성 |
|---|---|---|---|
| 0 아이콘 | 반나절 | 없음 | 설치 가능해짐 |
| 1 토스트 | 낮음 | 없음 | 즉시 보임 |
| 2 파사드 | 낮음 | 없음(동작 무변화) | 없음 — **키스톤** |
| 3 SW | 중간 | **높음** | 없음 |
| 4 매칭 필터 | ~3줄 | 낮음 | 매칭 품질 |
| 5a 푸시 백엔드 | 중간 | 낮음(다크 머지) | 없음 |
| 5b 푸시 프론트 | 중간 | 중간 | 큼 |
| 6 SMS | 낮음 | **재정적** | 큼 |

### 리스크 순위

1. **SW 마이그레이션 × CloudFront 캐싱 (Phase 3).** 잘못된 `sw.js`가 설치된 사용자에게 도달해 롤백 이후까지 남는다. 완화: 단독 배포, 산출 파일명을 워크플로 하드코딩 경로와 대조, `skipWaiting`/`clientsClaim`/`cleanupOutdatedCaches` 정확히 보존
2. **매칭 엔진 스레드의 블로킹 I/O.** 설계로 회피하지만(별도 유계 `notification-outbound` executor) 나중에 한 줄로 저지를 수 있는 실수다. **제약을 `NotificationService` Javadoc에 다음 사람이 읽을 자리에 박아둘 것**
3. **누락된 아이콘(Phase 0)** 이 tier 2 전체를 Android에서 무의미하게 만드는 것 — 지금 깨져 있고 아무것도 시끄럽게 에러를 내지 않아 놓치기 쉽다

---

## §9 검증 절차

### Phase 0 — 아이콘

Chrome DevTools → Application → Manifest: 아이콘 에러 0, **"Installable"** 표시. 실기기 iOS 홈화면 추가 시 스크린샷이 아니라 앱 아이콘이 나오는지. 각 PNG <30KB 확인.

### Phase 1 — 토스트

`pnpm lint && pnpm build`. 마이그레이션한 3개 화면에서 각 토스트 트리거 수동 확인. 동시 3개 초과 시 오래된 것이 밀려나는지. 같은 `dedupeKey`를 연속 발화했을 때 쌓이지 않고 교체되는지.

### Phase 2 — 파사드

`./gradlew test` (통합 태그 제외: `-PexcludeTags=integration`). `NotificationPolicyTest` 2개 통과.

**동작 무변화 확인이 핵심이다.** 백엔드에 이미 있는 정적 SSE 콘솔로 파사드 전/후를 비교해 이벤트 이름·순서·페이로드가 동일한지 대조:
- `backend/src/main/resources/static/sse-test.html`
- `backend/src/main/resources/static/matching-status.html`
- `backend/src/main/resources/static/delivery-test.html`

### Phase 3 — 서비스워커

1. `pnpm build && ls dist/` → `sw.js`, `registerSW.js`, `manifest.webmanifest` 존재 확인 (**워크플로가 이 이름을 하드코딩한다**)
2. `pnpm preview`로 실기기 접속:
   - 오프라인 상태에서 딥링크 하드 리프레시가 SPA 셸로 해석되는지
   - 재빌드 후 새 SW가 `waiting`에 갇히지 않고 즉시 활성화되는지(= `skipWaiting`/`clientsClaim` 확인)
   - DevTools → Application → Cache Storage에 낡은 캐시가 남지 않는지(= `cleanupOutdatedCaches` 확인)
   - `/api` 요청이 SW에 가로채이지 않는지

### Phase 4 — 매칭 필터

`SSE_연결이_없는_드리미는_오퍼_후보에서_제외된다` 테스트.

`matchingtest/run.mjs` 부하 하니스로 전/후 비교 — 다음이 모두 만족해야 한다:
- `sse.events.dropped{reason=not_connected}` 감소
- `matching.candidates.filtered{reason=not_connected}` 증가
- 매칭 성공까지의 라운드 수 감소

### Phase 5a — 푸시 백엔드

1. `GET /vapid-public-key`가 미설정 시 `publicKey: null` 반환
2. `web-push.enabled=true` + 로컬 VAPID 키 → 브라우저 하나에서 구독 후 `POST /push/subscriptions` → DB 행 확인
3. 같은 endpoint로 다른 계정 로그인 후 재구독 → **행이 2개가 되지 않고 `boormi_id`가 재배정**되는지
4. 브라우저에서 구독을 해지한 뒤 전송 → 410 수신 → 행 정리 + `push.subscriptions.pruned` 증가

### Phase 5b — 푸시 프론트 (실기기 필수)

**Android Chrome (설치본 + 미설치 탭 둘 다)** 및 **iOS 홈화면 설치본**에서:

1. 앱을 완전히 닫고 오퍼 발생 → 알림 도착 → 탭 → 앱이 열리며 `/matching`에 **올바른 카운트다운**이 뜨는지
2. 이미 열린 앱에서 탭했을 때 **새 창이 아니라 focus + 클라이언트 사이드 전환**이 되는지 (SSE 재연결이 일어나지 않아야 한다)
3. 오퍼가 만료된 뒤 탭했을 때 "요청이 이미 마감됐어요" 토스트가 뜨는지
4. 알림 3연타가 알림 3개로 쌓이지 않고 `tag`로 덮어써지는지
5. 카카오톡으로 링크를 보내 인앱브라우저 안내 카드가 뜨는지
6. 권한 거절 후 7일 안에 다시 묻지 않는지
7. `TTL: 30`이 걸린 오퍼 알림이 기기를 오래 꺼둔 뒤에 배달되지 **않는지**

### Phase 6 — SMS

1. `solapi.enabled=false`로 `DevSmsSender` 로그 확인 → 진행 중 배달에서 드리미 앱을 강제 종료하고 3분 대기 → **로그 1회만**
2. 그 상태에서 앱 재시작(배포 시뮬레이션) → **재발송 없음** (`offline_sms_sent_dtm` 영속 중복제거 확인)
3. 위치 재개 후 다시 3분 무소식 → **재장전되어 1회 발송** (컬럼 null 복귀 확인)
4. 문구 바이트 수 확인 — SOLAPI 콘솔에서 SMS로 나가는지 LMS로 승격되지 않았는지
5. 마지막으로 실제 번호 1건 수동 확인 후 `SMS_FALLBACK_ENABLED=true`
