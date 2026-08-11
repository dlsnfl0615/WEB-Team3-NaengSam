# 매칭 실경로 부하테스트

부하가 걸린 상태에서 **드리미·부르미 개인 단위로 매칭이 실제로 성사되는지**, **와야 할 알람이 유실되지 않는지**, **DB가 그 결과와 일치하는지**를 판정한다. 디버그 API는 쓰지 않는다 — 로그인·SSE·주문 생성·수락·확정을 전부 운영과 같은 경로로 태운다.

실행은 하나다.

```bash
cd matchingtest
npm install
cp config/env.example config/.env.local
cp config/users.example.json config/users.json
npm run loadtest
```

## 실행 전 준비

**백엔드를 `KAKAO_ENABLED=false`로 띄운다.** 주문 1건이 카카오 지오코딩 2회 + 도보 길찾기 1회를 태우므로, 실 API로 돌리면 주문 100건에 카카오 호출 300회가 나가 매칭과 무관한 곳에서 쿼터가 마른다. 이 값을 주면 `DevCoordinatesService` / `DevDirectionsService`가 주입돼 외부 호출 없이 좌표·거리를 로컬에서 계산한다(`KAKAO_REST_API_KEY`도 필요 없다). 주문 생성 경로 자체는 그대로라 결제·`MAX_ACTIVE_ORDERS` 가드·요금 계산·이벤트 순서는 전부 실제로 돈다.

IntelliJ 실행 구성의 환경변수 또는:

```bash
KAKAO_ENABLED=false ./gradlew bootRun
```

**프론트를 5173이 아닌 포트로 띄운다면 백엔드에 그 오리진을 허용시킨다.** Vite `/api` 프록시는 브라우저의 Origin 헤더를 그대로 백엔드에 넘기는데, 허용 목록(`cors.allowed-origins`) 기본값이 `http://localhost:5173` 하나뿐이라 다른 포트면 모든 API가 403 `Invalid CORS request`로 막힌다. 프론트는 건드리지 않고 백엔드 환경변수로 푼다:

```bash
KAKAO_ENABLED=false CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174 ./gradlew bootRun
```

1/5 단계에서 `WEB_BASE`의 오리진으로 프리플라이트를 한 번 보내 거부되면 중단한다(`WATCH=1`일 때만).

`KAKAO_CHECK=1`(기본값)이면 3/5 단계에서 스텁 여부를 확인하고, 실 API로 보이면 부하를 시작하기 전에 중단한다. 좌표·거리·요금이 실제 값이 아니게 되므로 Playwright 화면의 위치와 금액은 가짜다 — 매칭 순서는 거리와 무관한 전역 FIFO라 매칭 결과에는 영향이 없다.

## 구성

| 파일                 | 역할                                                             |
| -------------------- | ---------------------------------------------------------------- |
| `run.mjs`            | **유일한 진입점.** 5단계 오케스트레이션과 진행 출력, 리포트 생성 |
| `modules/db.mjs`     | JDBC 실행기(H2/MySQL), 테스트 데이터 한정 초기화, 검증 쿼리      |
| `modules/seed.mjs`   | 계정 SQL 생성(주문은 만들지 않는다 — 실 API로 만든다)            |
| `modules/drive.mjs`  | 부하 드라이버 (로그인 · SSE · 주문 생성 · 수락 · 확정)           |
| `modules/ledger.mjs` | 주문 단위 이벤트 원장 + 알람 유실 판정 + DB 대조                 |
| `modules/watch.mjs`  | Playwright 실클라이언트 검증(단언 포함)                          |
| `modules/report.mjs` | 리포트 렌더링                                                    |
| `config/users.json`  | Playwright가 쓸 브라우저 계정                                    |
| `config/.env.local`  | 실행 설정 (기본 `ENV_FILE`)                                      |
| `config/env.example` | 환경변수 템플릿                                                  |

생성물: `seed.sql`(감사용), `agents.json`(에이전트 명세), `result/<타임스탬프>.{json,md}`, `videos/`.

## 실행 단계

```
━━ 1/5  대상 확인       API / WEB / DB 연결 확인
━━ 2/5  DB 초기화       @test.local 계정과 그 계정의 주문만 삭제
━━ 3/5  유저 세팅       계정 SQL 생성 후 DB에 직접 적용
━━ 4/5  클라이언트 기동  Playwright 창을 매칭 대기까지 올린다
━━ 5/5  부하 시작       주문 램프 + 실시간 집계
━━ 검증                 DB 대조 후 리포트
```

단계별 재실행:

```bash
npm run db:reset   # 초기화만
npm run seed       # 계정 세팅만
npm run drive      # 부하만 (기존 agents.json 재사용)
npm run watch      # 브라우저 검증만
```

## 대상 환경 바꾸기

부하 경로가 전부 환경변수라 실서버도 같은 스크립트로 때린다.

```bash
ENV_FILE=config/.env.prod npm run loadtest
```

`config/.env.prod` 예:

```ini
API_BASE=https://api.example.com
WEB_BASE=https://example.com
DB_KIND=mysql
DB_URL=jdbc:mysql://db.example.com:3306/naengsam
DB_USER=loadtest
DB_PASSWORD=...
RESET_DB=0
```

**실서버 대상 실행은 실계정·실결제·실SMS에 영향을 준다.** 그래서 기본값이 `RESET_DB=0`이고, `DB_URL` 호스트가 `localhost`가 아니면 초기화는 `ALLOW_REMOTE_RESET=1` 없이는 거부된다. 초기화 범위 자체도 `BOORMI.email LIKE '%@test.local'` 계정과 그 계정이 만든 주문으로만 한정되며, `TRUNCATE`나 조건 없는 `DELETE`는 쓰지 않는다.

## 무엇을 판정하는가

### 알람 유실

주문 1건 = 원장 레코드 1개로 기록하고, 단계마다 뒤따라야 할 이벤트가 제한 시간 안에 왔는지 본다.

| 선행 사실                             | 기대 이벤트                                         | 미도착 판정                     |
| ------------------------------------- | --------------------------------------------------- | ------------------------------- |
| `POST /boormi/calls` 200              | 드리미 누군가에게 `offer_popup` (`T_OFFER`)         | `오퍼_미발송`                   |
| `POST /dreami/offers/{id}/accept` 200 | 그 부르미에게 `dreami_info` (`T_INFO`)              | `dreami_info_유실`              |
| 수락자 외 형제 오퍼 존재              | 각 드리미에게 `offer_closed` (`T_CLOSED`)           | `offer_closed_유실`             |
| `confirm-dreami` 200                  | `delivery_started_dreami` / `_boormi`(`T_DELIVERY`) | `배달시작알림_유실` (양쪽 개별) |
| 오퍼 발송 후 무응답                   | TTL 30초 뒤 `offer_closed` (`T_EXPIRE`)             | `만료알림_유실`                 |

제한 시간이 아직 지나지 않은 건은 유실이 아니라 **판정보류**로 따로 센다. 판정보류가 나오면 `DRAIN_MS`를 늘린다.

### DB 대조

주문별로 확인하고 불일치만 보고한다.

- `ORDER_CD == IN_PROGRESS`
- `ORDERS.DREAMI_ID == DELIVERY.DREAMI_ID ==` 원장이 관측한 수락자 → **엉뚱한 배차를 여기서 잡는다**
- `MATCHING.ACCEPTED_DTM` / `DELIVERY` 행 존재
- 한 드리미가 `IN_PROGRESS` 주문을 2건 이상 동시 보유(중복 배차)
- `PENDING_BOORMI_CONFIRMATION`에 멈춘 주문(부르미 확정 유실의 DB 흔적)

### 실클라이언트

브라우저 드리미는 `콜 수락` 버튼이 뜨는 것과 클릭 후 `/delivery-track` 도달을, 부르미는 `수락하기`와 `/delivery-detail` 도달을 단언한다. 스크린샷과 영상이 남는다.

드리미가 클릭 뒤 `선착순 마감`을 받으면 `선착순패배`로 기록하고 **통과로 센다** — 같은 오퍼를 에이전트 수백 명이 동시에 받는 상황에서 지는 것은 정상 동작이라, 실패로 세면 리포트가 늘 빨갛다. 팝업 자체가 안 뜨는 것(`매칭대기_타임아웃`)만 실패다.

부하보다 **먼저** 기동한다. 오퍼 배정이 거리 무관 전역 FIFO(`updatedAt` 오름차순)라, 브라우저 드리미가 큐 앞단을 잡아야 에이전트 100명 사이에서 오퍼를 받는다.

종료 코드는 유실 또는 DB 불일치 또는 브라우저 실패가 있으면 `1`, 없으면 `0`이다(설정 오류 등 중단은 `2`).

## 알아둘 것

- **실행 전 백엔드를 재시작하라.** 매칭 엔진이 전부 인메모리(단일 JVM)라 이전 런의 닫힌 방이 남아 재매칭 때 되살아나고, 어떤 API로도 제거되지 않는다. 리포트의 `원장에 없는 주문의 이벤트`가 0이 아니면 이걸 의심하면 된다.
- **완주 상한 = 드리미 계정 수.** 매칭이 성사돼 주문이 `IN_PROGRESS`가 되면 `DreamiService.goOnline`의 `countActiveOrders > 0` 가드에 걸려 그 계정은 다시 온라인이 될 수 없다. `ORDER_COUNT > DREAMI_COUNT`면 초과분은 구조적으로 미성사이며, 리포트가 이를 유실이 아니라 "완주 상한"으로 분류한다.
- **오퍼 배정에 거리 필터가 없다.** `MatchingService.orderingComparator`가 `updatedAt` 오름차순 전역 FIFO다(코드에 TODO로 남아 있다). 드리미별 오퍼 분포가 균등하지 않은 것은 버그가 아니라 현 구현의 성질이라, 리포트는 사실로만 보고한다.
- **주문 생성이 카카오 API 3회(지오코딩 2 + 길찾기 1)에 의존한다.** 그래서 백엔드를 `KAKAO_ENABLED=false`로 띄워 로컬 스텁으로 대체한다(위 "실행 전 준비"). 실 API로 돌릴 경우 생성 지연·실패는 매칭 성능이 아니라 외부 호출 비용이므로 리포트에서 지표를 분리해 두었고, `ORDER_RATE`로 초당 생성 수를 조절한다.
- **비밀번호는 백엔드와 같은 규격으로 해싱해 시드한다.** `PasswordHasher`(PBKDF2WithHmacSHA256 / salt 16바이트 / 210,000회 / 256bit)와 동일하게 계산해 `"<saltHex>:<hashHex>"`로 넣는다. 평문으로 넣으면 `UserService.login`의 `PasswordHasher.matches`가 항상 false라 로그인이 실패한다. 반복 횟수가 커서 같은 평문은 한 번만 계산해 재사용한다(시드 계정은 salt를 공유). 백엔드 해싱 규격이 바뀌면 `seed.mjs`의 `hashPassword`도 같이 고쳐야 한다.
- **DB는 h2 jar의 `org.h2.tools.Shell`을 자식 프로세스로 띄워 붙는다.** Node용 JDBC 드라이버가 없어서다. `java`가 PATH에 있어야 하고, jar는 `~/.gradle` 캐시에서 자동으로 찾는다(`JDBC_JARS`로 직접 지정 가능).
- `MatchingDebugController`와 `DeliveryTestController`는 이제 이 테스트가 쓰지 않는다. 다만 `@PublicApi` / 전 프로파일 활성 상태로 남아 있으므로 정리는 별도 이슈로 다루는 것을 권한다.
