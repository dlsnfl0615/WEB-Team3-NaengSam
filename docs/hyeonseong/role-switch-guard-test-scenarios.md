# 부르미 ↔ 드리미 전환 차단 테스트 시나리오

매칭·배달이 진행 중일 때 역할 전환을 막고, 로그인 시 현재 상태에 맞는 화면으로 보내는 기능의 수동 검증 절차.

## 배경 — 무엇이 문제였나

진행 상태는 **이미 대부분 `ORDERS`에 남아 있었다.** `DreamiService.acceptOffer()`가 `markPendingBoormiConfirmation(dreamiId)`로 `order_cd`와 `dreami_id`를 함께 쓰기 때문에, "드리미가 수락하고 부르미가 대기 중"인 구간도 DB에 흔적이 있다. 실제 구멍은 네 가지였다.

| # | 문제 |
| --- | --- |
| G1 | 드리미 → 부르미 전환이 서버 검사를 거치지 않았다. `changeRole` API에 방향 파라미터가 없어 프론트가 부르미 방향엔 호출조차 하지 않았다 |
| G2 | 드리미 **온라인 대기** 상태만 DB에 없다. `MatchingService.dreamiMap`(인메모리)에만 존재한다 |
| G3 | `getUserInfo`가 `PENDING_BOORMI_CONFIRMATION` 드리미를 `ActiveRole.BOORMI`로 오판했다 |
| G4 | 부르미의 `activeOrderId`가 항상 null이라 진행 화면으로 복귀할 수 없었다 |

해결은 새 컬럼 없이 `UserActivityResolver` 하나가 `ORDERS`(내구성)와 `dreamiMap`(휘발성)을 합쳐 판정하고, 전환 검사·`/me`·`goOnline`이 모두 그것을 쓰도록 한 것이다. 차단 규칙은 한 줄이다 — **수행 중인 역할이 있고 그것이 요청한 역할과 다르면 차단**.

## 준비

계정 2개가 필요하다.

- **A** — 부르미
- **B** — 드리미 승인 완료 (부르미 겸용)

```bash
# 백엔드
cd backend && set -a && . ./.env && set +a && ./gradlew bootRun

# 프론트
cd frontend && pnpm dev
```

상태 확인용 API (세션 쿠키 필요, 브라우저 콘솔 또는 curl):

- `GET /api/v1/user/me` → `activeRole`, `activeOrderId`, `activeOrderCd`
- `GET /api/v1/user/role?target=BOORMI` / `?target=DREAMI` → 전환 가능 여부

### 에러 코드

| 코드 | 상수 | 메시지 |
| --- | --- | --- |
| `USER_003` | `DREAMI_NOT_REGISTERED` | 드리미 등록 후 이용할 수 있어요. |
| `USER_004` | `DREAMI_NOT_APPROVED` | 드리미 승인 후 이용할 수 있어요. |
| `USER_006` | `CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER` | 수행 중인 주문이 있어 전환할 수 없어요. |
| `USER_007` | `CANNOT_CHANGE_ROLE_WHILE_MATCHING` | 매칭 대기 중에는 전환할 수 없어요. 먼저 오프라인으로 전환해주세요. |

### 우선순위

시간이 부족하면 **3 → 2 → 4 → 12 → 16** 다섯 개만으로 핵심이 검증된다. **시나리오 3이 가장 중요하다.**

---

## 그룹 1 — 전환 차단 (핵심)

### 1. 부르미가 주문 등록한 상태

1. A 로그인 → 부르미 모드
2. A가 주문 등록 (`/request-create`)
3. A의 `/me` 확인 → `activeRole: BOORMI`, `activeOrderCd: MATCHING`
4. A가 홈에서 토글을 **드리미**로 시도

**기대** — 토글 비활성, 사유 `"매칭 중인 주문이 있어 전환할 수 없어요."`
API 직접 호출 `?target=DREAMI` → `USER_006`

### 2. 드리미가 매칭 등록만 한 상태 (온라인 대기)

> 기존에 DB에 흔적이 없어 막지 못하던 케이스 (G2).

1. B 로그인 → 드리미로 전환 → `/matching`에서 **온라인** 전환
2. B의 `/me` 확인 → `activeRole: DREAMI`, `activeOrderId: null`, `activeOrderCd: null`
3. B가 토글을 **부르미**로 시도

**기대** — 토글 비활성, 사유 `"매칭 대기 중에는 전환할 수 없어요. 먼저 오프라인으로 전환해주세요."`
API 직접 호출 `?target=BOORMI` → **`USER_007`** (USER_006이 아니어야 한다)

### 3. 드리미가 수락하고 부르미가 대기 중 ★

> 역할 오판(G3) 회귀 방지 케이스. 가장 중요하다.

1. A가 주문 등록 → B는 온라인 상태로 오퍼 팝업 수신
2. **B가 수락** → A에게 드리미 확인 팝업이 뜬다. **A는 아직 누르지 않는다**
3. B의 `/me` 확인 → **`activeRole: DREAMI`**, `activeOrderCd: PENDING_BOORMI_CONFIRMATION`
   - 여기서 `BOORMI`가 나오면 **회귀다**
4. B가 부르미로 전환 시도 → 차단, `"부르미 확인을 기다리는 중이라 전환할 수 없어요."`
5. 같은 시점 A의 `/me` → `activeRole: BOORMI`, `activeOrderCd: PENDING_BOORMI_CONFIRMATION`
6. A가 드리미로 전환 시도 → 차단, `"드리미 확인이 남아 있어 전환할 수 없어요."`

**기대** — 양쪽 API 모두 `USER_006`

> ⏱ 30초 안에 확인해야 한다. 넘기면 시나리오 25의 알려진 버그 상태가 된다.

### 4. 배달 진행 중 (양방향)

1. 시나리오 3에서 **A가 확정** → 배달 시작
2. 양쪽 `/me` → `activeOrderCd: IN_PROGRESS` (A는 `BOORMI`, B는 `DREAMI`)
3. A가 드리미로 시도 → 차단, `"배달이 진행 중이라 전환할 수 없어요."`
4. B가 부르미로 시도 → 차단, 같은 문구

**기대** — 양쪽 API 모두 `USER_006`

### 5. 아무것도 없을 때 정상 전환

1. 시나리오 4의 배달을 완료 또는 취소
2. 양쪽 `/me` → `activeRole: null`
3. A·B 각각 양방향 전환 시도

**기대** — 토글 활성, 전환 성공, 사유 문구 없음

---

## 그룹 2 — 전환 API 자체

### 6. 같은 역할로의 전환은 통과

1. B가 온라인 대기 중 (`activeRole: DREAMI`)
2. `GET /api/v1/user/role?target=DREAMI`

**기대** — 200 성공 (자기 역할로의 요청은 무변화라 통과시킨다)

### 7. 부르미 전환은 드리미 승인 검사를 하지 않는다

1. 드리미 **미등록** 계정 C 로그인
2. `?target=BOORMI` → **200 성공** (`USER_003`이 나오면 안 된다)
3. `?target=DREAMI` → `USER_003`

### 8. 드리미 심사 중 계정

1. 드리미 신청만 하고 승인되지 않은 계정 D
2. `?target=DREAMI` → `USER_004`
3. 프론트에서 토글을 드리미로 → **`/verify`로 이동** (기존 동작 유지 확인)

### 9. target 파라미터 누락

1. 로그인 상태에서 `GET /api/v1/user/role` (target 없이)

**기대** — 400 (필수 파라미터 누락)

---

## 그룹 3 — 드리미 온라인 전환 차단

### 10. 부르미로 주문이 있으면 온라인 전환 불가

1. B가 **부르미로** 주문을 등록
2. B가 드리미로 전환 시도 → 차단 (시나리오 1과 동일)
3. 강제로 `POST /api/v1/dreami/status/online` 호출

**기대** — `ALREADY_HAS_ACTIVE_ORDER`

### 11. 배달 중 온라인 전환 불가

1. B가 배달 진행 중 (`IN_PROGRESS`)
2. `POST /api/v1/dreami/status/online`

**기대** — `ALREADY_HAS_ACTIVE_ORDER`

---

## 그룹 4 — 로그인 라우팅

### 12. 배달 중 재로그인 → 진행 화면 복귀

1. 시나리오 4 상태(배달 중)에서 A·B 모두 로그아웃
2. **B 재로그인** → `/delivery-track?orderId=…`로 바로 진입
3. **A 재로그인** → `/delivery-detail?orderId=…`로 바로 진입

### 13. 온라인 대기 중 재로그인 → 매칭 화면

1. B가 온라인 대기 중 → 로그아웃 → 재로그인

**기대** — `/matching`으로 진입

> ⚠️ 로그아웃으로 세션이 끊기면 서버가 `dreamiMap`에서 B를 정리할 수 있다. 정리되면 `activeRole: null`이라 `/home`으로 간다. **어느 쪽이 나오는지 기록할 것** — 로그아웃과 매칭 등록 해제의 연동 여부가 이 시나리오로 드러난다.

### 14. 매칭 중 재로그인 → 홈

1. A가 주문 등록만 한 상태(`MATCHING`) → 로그아웃 → 재로그인

**기대** — `/home`으로 진입하고, 매칭 팝업이 SSE/`syncCurrentMatching`으로 복원된다

### 15. 로그인된 채로 `/login` 직접 진입

1. 배달 중인 B가 주소창에 `/login` 입력

**기대** — `/delivery-track?orderId=…`로 리다이렉트 (이전에는 무조건 `/home`이었다)
`/` 와 `/signup` 으로도 동일하게 확인한다.

---

## 그룹 5 — 역할 복원 / 상태 갱신

### 16. 새로고침 시 역할 유지

1. B가 온라인 대기 중 (드리미 모드)
2. **F5 새로고침** → 드리미 모드 유지, 토글 잠김
3. **다른 탭**에서 같은 URL 열기

**기대** — sessionStorage가 비어 있어도 서버 `activeRole`로 **드리미**가 뜬다 (기존에 안 되던 부분, G4)

### 17. 새로고침은 화면을 튕기지 않는다

1. B가 배달 중, `/delivery-track`에서 F5

**기대** — `/delivery-track`에 그대로 머무른다.
의도된 동작이다 — 새로고침 시에는 리다이렉트하지 않는다. 보고 있던 화면에서 강제로 튕겨내는 부작용이 더 크기 때문이다.

### 18. 화면 진입 시 잠금 갱신

1. B가 배달 중인데 홈이 아닌 다른 화면에 있다
2. `/home` → `/activity` → `/earnings` 순회

**기대** — 세 화면 모두 토글이 잠기고 사유가 표시된다

### 19. SSE 재연결 시 갱신

1. 배달 중 상태에서 네트워크를 잠깐 끊었다 복구 (또는 서버 재기동)

**기대** — SSE `connected` 이벤트 후 `/me`가 다시 호출되어 잠금 상태가 유지된다

---

## 그룹 6 — 경계 / 회귀

### 20. 부르미가 드리미를 거절

1. 시나리오 3 상태에서 **A가 거절**

**기대** — 주문이 `MATCHING`으로 복귀, B의 `activeRole`이 풀려 전환 가능해진다

### 21. 드리미가 오퍼를 거절

1. B에게 오퍼 팝업 → **거절**

**기대** — B는 여전히 온라인 대기(`DREAMI`, `orderId` null) → 부르미 전환은 계속 `USER_007`

### 22. 드리미 30초 미응답

1. 오퍼 팝업이 뜬 B가 아무것도 누르지 않고 30초 경과

**기대** — DB 변화 없음, B는 온라인 대기 유지, 전환은 계속 `USER_007`

### 23. 서버 재시작 후 온라인 상태

1. B가 온라인 대기 중 → **백엔드 재시작**
2. B의 `/me` → `activeRole: null`

**기대** — 전환 허용. **의도된 동작이다** — 재시작하면 실제로도 오프라인이 맞다. 영속 컬럼 대신 인메모리를 진실 소스로 둔 이유가 이것이다(드리프트가 없다).

### 24. 부르미 다건 주문

1. A가 주문을 2건 등록 (`MAX_ACTIVE_ORDERS` 한도 내)

**기대** — `activeOrderId`가 가장 최근 주문, 전환은 계속 차단

---

## 25. 알려진 미수정 버그 — 재현되는 것이 정상

> 이번 범위에서 **의도적으로 제외**한 건이다. 버그가 재현되어야 정상이며, 회귀로 보고하지 말 것.

1. A가 주문 등록 → B가 수락
2. A가 드리미 확인 팝업을 **30초 넘게 방치**

관찰되는 것:

- 주문이 DB에 `PENDING_BOORMI_CONFIRMATION` + `dreami_id = B`로 **그대로 남는다**
- **B는 영구히 잠긴다** — `/me`가 계속 `activeRole: DREAMI`, 전환·온라인 전환 모두 `USER_006`
- 그 주문을 **다른 드리미가 수락 시도**하면 `ALREADY_ACCEPTED_BY_OTHER`로 실패 → **영구 매칭 불가**
- 복구하려면 DB에서 해당 주문을 직접 손봐야 한다

원인은 `MatchingService.applyExpireBoormiOffer`가 인메모리 상태만 정리하고 DB를 되돌리지 않는 것이다. 별도 이슈로 분리해 `order.rejectDreami()`(이미 존재, `Orders.java`)를 커밋 후 호출하는 방식으로 처리한다.

---

## 참고 — 판정 규칙

`UserActivityResolver.resolve(userId)`의 우선순위:

1. `ORDERS`에서 `dreami_id = userId` 이고 상태가 진행 중 → **DREAMI** + 주문
2. `ORDERS`에서 `boormi_id = userId` 이고 상태가 진행 중 → **BOORMI** + 최신 주문
3. `MatchingService.isDreamiWaiting(userId)` → **DREAMI**, 주문 없음
4. 그 외 → 비활성 (`role = null`)

진행 중으로 보는 상태는 `MATCHING`, `PENDING_BOORMI_CONFIRMATION`, `IN_PROGRESS`, `WAITING_CONFIRMATION` 네 가지다.

드리미를 부르미보다 **먼저** 보는 것이 핵심이다. 부르미 확인 대기 중인 주문은 `boormi_id`·`dreami_id` 양쪽으로 잡히므로, 순서를 뒤집으면 드리미가 부르미로 오판된다(G3).
