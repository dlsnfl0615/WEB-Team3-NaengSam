# 매칭 실경로 부하테스트

부하가 걸린 상태에서 **개인 단위로 매칭이 성사되는지**, **와야 할 알람이 유실되지 않는지**, **DB가 그 결과와 일치하는지**를 판정한다. 디버그 API는 쓰지 않는다 — 로그인·SSE·주문 생성·수락·확정을 전부 운영과 같은 경로로 태운다.

짧게 훑으려면 [QUICKSTART.md](QUICKSTART.md)를 본다. 이 문서는 상세판이다.

```bash
cd matchingtest
npm install
cp config/env.example config/.env.local
cp config/users.example.json config/users.json
npm run loadtest
```

## 실행 전 준비

**백엔드를 `KAKAO_ENABLED=false`로 띄운다.** 주문 1건이 카카오 API 3회(지오코딩 2 + 길찾기 1)를 태워 실 API로는 쿼터가 먼저 마른다. 이 값을 주면 좌표·거리를 로컬에서 계산하고, 주문 생성 경로 자체는 그대로 돈다.

```bash
KAKAO_ENABLED=false ./gradlew bootRun
```

**프론트가 5173이 아니면 그 오리진을 백엔드에 허용시킨다.** 허용 목록 기본값이 `http://localhost:5173` 하나뿐이라 다른 포트면 모든 API가 `403 Invalid CORS request`로 막힌다.

```bash
KAKAO_ENABLED=false CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174 ./gradlew bootRun
```

두 가지를 실행 초반에 스스로 확인하고, 어긋나면 부하를 시작하기 전에 중단한다 — `CORS_CHECK=1`은 `WEB_BASE` 오리진으로 프리플라이트를 보내고, `KAKAO_CHECK=1`은 카카오가 스텁인지 본다. 배포 대상처럼 검사가 거짓 실패를 내는 환경에서는 각각 0으로 끈다.

## 실행 단계

```
━━ 1/5  대상 확인       API / WEB / DB 연결 확인
━━ 2/5  DB 초기화       @test.local 계정과 그 계정의 주문만 삭제
━━ 3/5  유저 세팅       계정 SQL 생성 후 DB에 직접 적용
━━ 4/5  클라이언트 기동  Playwright 창을 매칭 대기까지 올린다
━━ 5/5  부하 시작       주문 램프 + 실시간 집계
━━ 검증                 DB 대조 후 리포트
```

```bash
npm run db:reset   # 초기화만
npm run seed       # 계정 세팅만
npm run drive      # 부하만 (기존 agents.json 재사용)
npm run watch      # 브라우저 검증만
```

## 파일 구성

`run.mjs`가 유일한 진입점이고, 나머지는 전부 `modules/`에 있다 — `db`(JDBC 실행기·한정 초기화·검증 쿼리) · `seed`(계정 SQL 생성) · `pull`(기존 계정 조회, SELECT만) · `drive`(로그인·SSE·주문·수락·확정) · `cleanup`(남은 주문 취소) · `ledger`(이벤트 원장·유실 판정·DB 대조) · `watch`(Playwright 검증) · `report`(렌더링).

설정은 `config/`에 있다 — `.env.local`(실행 설정, 기본 `ENV_FILE`) · `env.example`(템플릿) · `users.json`(브라우저 계정).

### 로그인 대기열 (#399)

`POST /api/v1/user/login`은 200이어도 로그인이 끝난 것이 아니다. 동시 로그인이 서버의 해싱 슬롯(`login.queue.permits`)을 넘기면 세션 대신 대기 티켓(`result.status === "QUEUED"`)이 온다. 이때 하네스는 `POST /api/v1/user/login/queue/{ticketId}`를 서버가 준 `pollAfterMs` 간격으로 폴링하고, **세션 쿠키는 차례가 된 그 폴링 응답에서** 받는다. 티켓 ID는 최초 응답에만 실려 오므로 폴링 내내 같은 티켓을 쓴다.

대기 상한은 `LOGIN_QUEUE_TIMEOUT_MS`(기본 120초, 서버 티켓 TTL과 맞췄다)다. 정원 초과(503)와 티켓 만료(410)는 두 번까지 로그인을 처음부터 다시 탄다. 대기열이 없는 백엔드에서는 응답에 `result`가 없어 곧바로 기존 즉시 경로로 떨어지므로, 같은 스크립트가 양쪽에서 다 돈다.

브라우저 검증(`watch`)도 같은 대기열을 만난다. 대기 모달이 뜨면 **폼을 다시 제출하지 않고** 화면이 스스로 `/home`으로 넘어갈 때까지 기다린다 — 재제출은 티켓만 하나 더 만들고 순번을 뒤로 민다.

결과는 리포트 `11. 로그인 대기열` 섹션(JSON은 `loginQueue`)에 남는다. 대기 시간이 길면 서버의 `login.queue.permits`를, 503이 잡히면 `login.queue.capacity`를 먼저 본다.

생성물: `seed.sql`(감사용), `agents.json`(에이전트 명세), `result/<타임스탬프>.{json,md}`, `videos/`.

## 리포트 읽는 법

### 지연

모든 구간의 기준점은 응답 수신이 아니라 **요청 발신 시각**이다. 응답을 기준으로 삼으면 API 왕복이 어느 지표에도 잡히지 않고, 엔진이 응답보다 먼저 SSE를 보내는 정상 상황에서 구간이 음수가 된다.

| 리포트 절           | 무엇                                           |
| ------------------- | ---------------------------------------------- |
| 2. API 응답시간     | 요청 발신 → 응답 수신. 톰캣 큐 + 트랜잭션 + DB |
| 3. 서버 비동기 경로 | 요청 발신 → 해당 SSE 도착. 매칭 엔진 경로 전체 |

둘 다 부하 드라이버 안에서만 잰다. 백엔드에는 계측 코드가 없다.

3절이 2절보다 크게 부풀면 병목은 동기 처리가 아니라 매칭 엔진의 비동기 경로다 — 배치창 2초 + 주문당 `max-concurrent-offers`개 팬아웃이라, 주문이 몰리면 웨이브가 밀려 초 단위로 늘어난다. 3절 끝에 함께 찍히는 harness 이벤트 루프 지연이 크면 SSE 수신 시각 자체가 밀린 것이니 서버를 탓하기 전에 이 값을 먼저 본다.

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

- 기본값은 드리미 10명 · 부르미 10명(`WATCH_DREAMI` / `WATCH_BOORMI`). 계정은 `config/users.json`에서 역할별로 앞에서부터 뽑고, **모자라면 조용히 줄이지 않고 즉시 중단한다.** 리포트 8번 절 머리줄의 `실행 드리미 10/10 · 부르미 10/10`을 먼저 본다. 부르미 계정의 `order`는 비워도 된다 — `seed.mjs`의 강남·서초 주소 목록에서 뽑아 쓴다.
- 드리미가 클릭 뒤 `선착순 마감`을 받으면 `선착순패배`로 기록하고 **통과로 센다.** 팝업 자체가 안 뜨는 것(`매칭대기_타임아웃`)만 실패다.
- 창은 유저당 하나이고 순차로 띄우며(동시에 올리면 크롬 스파이크로 첫 로그인들이 타임아웃난다) `HEADED=0`이 기본이다. 부하보다 **먼저** 기동한다 — 오퍼 배정이 거리 무관 전역 FIFO라 큐 앞단을 잡아야 에이전트 사이에서 오퍼를 받는다.

종료 코드는 유실 또는 DB 불일치 또는 브라우저 실패가 있으면 `1`, 없으면 `0`이다(설정 오류 등 중단은 `2`).

## 실서버 대상 실행

부하 경로가 전부 환경변수라 실서버도 같은 스크립트로 때린다.

```bash
ENV_FILE=config/.env.prod npm run loadtest
```

운영 대상은 이 접두사를 매번 붙이는 대신 스크립트로 남겨뒀다.

```bash
npm run tunnel        # DB SSH 터널 (포그라운드, 별도 터미널에서 띄워둔다)
npm run prod          # 전체 실행
npm run prod:seed     # 계정 수집만 — 잔여 주문 사전 점검도 여기서 찍힌다
npm run prod:cleanup  # 잔여 주문 조회 (취소하려면 CLEANUP_CONFIRM=1)
```

`tunnel`은 `~/.ssh/config`의 `symboorm-web` 호스트를 쓴다. 터널은 웹 인스턴스를 경유해야 한다 — DB 인스턴스로 바로 포워딩하면 MySQL 권한이 `'symboorm'@'localhost'`로 잡혀 로그인이 거부된다.

`config/.env.prod` 예:

```ini
API_BASE=https://api.example.com
WEB_BASE=https://example.com
DB_KIND=mysql
DB_URL=jdbc:mysql://db.example.com:3306/naengsam
DB_USER=loadtest
DB_PASSWORD=...

RESET_DB=0
REMOTE_DB=1
CORS_CHECK=0            # 프론트와 API가 같은 오리진이면 서버가 CORS 헤더를 안 줘 거짓 실패한다
KAKAO_CHECK=0           # 카카오 실호출이면 스텁 판정(50ms 임계)에 무조건 걸린다

USE_EXISTING_ACCOUNTS=1
EXISTING_EMAIL_LIKE=%@test.local
EXISTING_PASSWORD=...
```

**실서버 대상 실행은 실계정·실결제·실SMS에 영향을 준다.** `DB_URL` 호스트가 `localhost`가 아니면 초기화는 `ALLOW_REMOTE_RESET=1` 없이는 거부된다. 초기화 범위 자체도 `BOORMI.email LIKE '%@test.local'` 계정과 그 계정이 만든 주문으로만 한정되며, `TRUNCATE`나 조건 없는 `DELETE`는 쓰지 않는다. 다만 **`RESET_DB`의 기본값은 `1`이므로 실서버 설정 파일에 `RESET_DB=0`을 반드시 명시해야 한다.**

DB에 SSH 터널로 붙는 경우(`jdbc:mysql://127.0.0.1:13306/...`) 호스트가 `localhost`로 보여 위 가드가 그냥 통과한다. 터널을 쓸 때는 `REMOTE_DB=1`을 함께 줘야 가드가 산다.

### 계정을 만들지 않고 빌려 쓰기

운영 DB는 INSERT가 곤란하다. `USE_EXISTING_ACCOUNTS=1`이면 3/5 단계가 시드 SQL을 만들지 않고, `EXISTING_EMAIL_LIKE` 패턴에 맞는 계정을 DB에서 조회해(`modules/pull.mjs`) 그대로 에이전트로 쓴다. DB 작업은 `SELECT`뿐이다.

비밀번호는 DB에 PBKDF2 해시로만 있어 복원할 수 없다. 대상 계정들이 공통으로 쓰는 평문을 `EXISTING_PASSWORD`로 줘야 하고, 틀리면 로그인 단계에서 전부 실패한다.

수집 조건은 시드가 지키던 제약과 같다. 드리미 풀은 `DREAMI.request_cd='APPROVED'`이면서 활성 주문이 없는 계정(주문을 가진 계정은 `DreamiService.goOnline`에서 막힌다), 부르미 풀은 `DREAMI` 행이 없는 계정이다. 두 풀은 겹치지 않고, `config/users.json`의 브라우저 계정도 제외된다. 요청한 수만큼 없으면 조용히 줄이지 않고 중단한다.

DB에 안 쓴다고 해서 부작용이 없는 것은 아니다. 주문은 실제 API로 만들어지므로 `ORDER_COUNT + WATCH_BOORMI`만큼의 실주문과 그에 딸린 결제·포인트 원장이 실계정 명의로 남는다.

### 남은 주문 정리

부하는 부르미 확정까지만 태우고 끝나므로 성사된 주문이 `IN_PROGRESS` + `DELIVERY(PICKUP_NORMAL)`로 남는다. 활성 주문 1건이 부르미 1명과 드리미 1명을 동시에 수집 대상에서 빼므로, 정리하지 않으면 몇 번 돌리는 사이 쓸 계정이 없어진다.

그래서 계정 수집(3/5) 직전에 잔여 활성 주문을 세어 보여준다. `USE_EXISTING_ACCOUNTS=1`일 때만 돈다.

```
수집  DB에 있는 계정 재사용 (INSERT 없음) — 패턴 %@test.test
잔여  활성 주문 12건 — IN_PROGRESS/PICKUP_NORMAL 11 · PENDING_BOORMI_CONFIRMATION 1
  ⚠  이 주문들이 부르미·드리미 계정을 잠급니다. npm run cleanup 으로 확인하세요.
```

경고만 하고 멈추지는 않는다. 계정이 충분하면 잔여 주문이 있어도 실행에 지장이 없고, 정말 모자라면 그다음 수집 단계가 중단시킨다.

`CLEANUP=1`(기본값)이면 리포트를 낸 **뒤에** 이번 런이 만든 주문을 전부 취소한다. 순서가 중요하다 — 검증이 `order_cd = 'IN_PROGRESS'`를 보기 때문에 취소가 먼저 오면 리포트가 통째로 거짓 실패를 낸다.

주문 상태에 따라 취소 API가 다르고(`modules/cleanup.mjs`), 둘 다 **주문을 만든 부르미 본인 세션**을 요구한다.

| 주문 상태                                 | 엔드포인트                                             |
| ----------------------------------------- | ------------------------------------------------------ |
| `MATCHING`, `PENDING_BOORMI_CONFIRMATION` | `DELETE /api/v1/boormi/calls/{orderId}`                |
| `IN_PROGRESS`(픽업 단계)                  | `POST /api/v1/delivery/orders/{orderId}/cancel/boormi` |

`DELETE /boormi/calls`는 결제 포인트를 전액 환불하지만 배달 취소 경로에는 환불이 없다. 반복해서 돌리면 테스트 부르미의 지갑 잔액이 줄어들기만 하므로 가끔 확인해야 한다.

이전 런들이 남기고 간 잔여 주문은 따로 치운다. DB에서 `EXISTING_EMAIL_LIKE`에 걸리는 계정의 활성 주문을 찾아(SELECT만) 같은 방식으로 취소한다.

```bash
npm run cleanup                       # dry-run — 상태별 건수만 출력
CLEANUP_CONFIRM=1 npm run cleanup     # 실제 취소
```

**취소는 되돌릴 수 없다.** `CLEANUP_CONFIRM=1` 없이는 절대 취소하지 않는 이유가 이것이다. `EXISTING_EMAIL_LIKE`가 넓으면(`%` 등) 실사용자 주문까지 취소되므로 dry-run 목록을 먼저 확인한다.

## 주의사항

- **실행 전 백엔드를 재시작하라.** 매칭 엔진이 전부 인메모리(단일 JVM)라 이전 런의 닫힌 방이 남아 재매칭 때 되살아나고, 어떤 API로도 제거되지 않는다. 리포트의 `원장에 없는 주문의 이벤트`가 0이 아니면 이걸 의심한다.
- **완주 상한 = 드리미 계정 수.** 매칭이 성사돼 주문이 `IN_PROGRESS`가 되면 `DreamiService.goOnline`의 `countActiveOrders > 0` 가드에 걸려 그 계정은 다시 온라인이 될 수 없다. `ORDER_COUNT > DREAMI_COUNT`면 초과분은 구조적으로 미성사이며, 리포트가 이를 유실이 아니라 "완주 상한"으로 분류한다.
- **브라우저 계정도 같은 풀에 들어간다.** 실수요는 `ORDER_COUNT + WATCH_BOORMI`, 공급은 `DREAMI_COUNT + WATCH_DREAMI`다. 둘이 같으면 오퍼가 한 번 만료되는 것만으로 남은 주문이 굶는다. 5/5 단계가 두 값을 찍고 여유가 없으면 경고하니, 10건쯤 두는 것이 좋다.
- **오퍼 배정에 거리 필터가 없다.** `MatchingService.orderingComparator`가 `updatedAt` 오름차순 전역 FIFO다(코드에 TODO로 남아 있다). 드리미별 오퍼 분포가 균등하지 않은 것은 버그가 아니라 현 구현의 성질이라, 리포트는 사실로만 보고한다.
- **비밀번호는 백엔드와 같은 규격으로 해싱해 시드한다.** `PasswordHasher`(PBKDF2WithHmacSHA256 / salt 16바이트 / 210,000회 / 256bit)와 동일하게 `"<saltHex>:<hashHex>"`로 넣는다. 백엔드 규격이 바뀌면 `seed.mjs`의 `hashPassword`도 같이 고쳐야 한다.
- **DB는 h2 jar의 `org.h2.tools.Shell`을 자식 프로세스로 띄워 붙는다.** Node용 JDBC 드라이버가 없어서다. `java`가 PATH에 있어야 하고, jar는 `~/.gradle` 캐시에서 자동으로 찾는다(`JDBC_JARS`로 직접 지정 가능).
