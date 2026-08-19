# Boormi 도메인

이름은 `boormi`지만 실제로는 **부르미 계정 엔티티 + 주문 접수/견적/취소/더블컨펌 + 절감액 대시보드**가 섞여 있다.
회원가입·로그인·내 정보 같은 계정 CRUD는 [User 도메인](../user/overview.md)에 있고, 이 도메인의 컨트롤러는 전부 "주문 관점"이다.

`BoormiService`는 사실상 오케스트레이터다 — address(좌표/길찾기), order, payment, matching, delivery 다섯 도메인에 의존한다.

## 1. `Boormi` 엔티티와 PK 공유 구조

```
boormi_id           BINARY(16) (PK, 앱에서 UUID.randomUUID() 생성)
email               unique
password            varchar(255)  — salt:hash 97자 (User 도메인 참고)
name / phone_number / birthdate
user_cd             ACTIVE | DELETED | RESTRICTED | BANNED
is_dreami_activated boolean, default false
is_admin            boolean, default false
boormi_avg_score    decimal(3,2), default 0  — insertable=false, 리뷰 등록 시 UPDATE로만 갱신
created/updated/deleted/restricted_dtm
```

스키마는 `backend/sql/sym-boorm-ddl.sql`로만 만든다(JPA는 `ddl-auto=validate`).

### 모든 드리미는 부르미다

DDL에 `FK_BOORMI_TO_DREAMI_1: DREAMI.dreami_id → BOORMI.boormi_id`가 걸려 있다. 즉 **`DREAMI.dreami_id`는 PK이자 BOORMI로의 FK**이고, 한 사람이 부르미이자 드리미일 때 두 테이블이 같은 UUID를 공유한다. 계정 정보(이메일/비밀번호/이름/전화/평점/권한)는 전부 BOORMI에, 심사 관련 필드만 DREAMI에 있다.

이 구조 덕에 `countActiveOrders`가 이렇게 쓸 수 있다.

```sql
WHERE order_cd NOT IN ('COMPLETED','CANCELLED','CLAIM_REVIEW')
  AND (boormi_id = :userId OR dreami_id = :userId)
```

같은 UUID이므로 **겸업 계정의 부르미 진행 건수와 드리미 진행 건수를 한 쿼리로 합산**한다. [Payment 도메인](../payment/overview.md)의 정산도 같은 전제에 기대어 `walletRepository.findByBoormiId(dreamiId)`로 드리미 지갑을 찾는다.

### `isDreamiActivate` × `requestCd` — 상태가 두 테이블로 쪼개진 대가

"드리미로 활동 가능한가"는 **두 테이블의 AND**다.

```
BOORMI.is_dreami_activated == true  AND  DREAMI.request_cd == APPROVED
```

판정은 `DreamiActivationChecker`가 전담한다. 리포지토리에만 의존하는 잎(leaf) 컴포넌트로 만들었는데, 서비스를 참조하면 매칭 ↔ 배달 ↔ 유저 순환 참조가 생기기 때문이다.

이 분리 때문에 실제 버그가 났다 — 관리자가 승인할 때 DREAMI만 `APPROVED`로 바꾸고 BOORMI의 `is_dreami_activated`는 그대로 둬서, **승인해도 드리미 기능이 켜지지 않았다**(`1da3332b`). 지금은 `Boormi.approve()`를 신설해 `DreamiService`가 두 엔티티를 함께 갱신한다.

또 하나: `isDreamiActivate`로 화면을 게이팅하면 `REQUESTED`/`REJECTED`가 미신청과 구분되지 않는다(둘 다 조회를 건너뛰므로). 그래서 `/me` 응답에 `dreamiStatus`(nullable)를 별도로 내린다.

`isAdmin`은 관리자 권한의 단일 진실 소스다. `AdminUserArgumentResolver`가 `@AdminUser` 파라미터를 resolve할 때마다 BOORMI를 한 번 조회한다.

## 2. API

base: `/api/v1/boormi`. **`@PublicApi`가 하나도 없다** → 인터셉터 기본 규칙에 따라 전 엔드포인트가 로그인 필수다.

| Method | Path | 설명 |
|---|---|---|
| POST | `/expected-value` | 견적(요금·예상시간·거리) |
| POST | `/calls` | 콜 등록 → orderId |
| GET | `/calls` | 내 주문 목록 (status 필터 / cursor / size) |
| GET | `/calls/{orderId}` | 주문 단건 |
| GET | `/calls/count` | 전체 건수 |
| GET | `/calls/status-counts` | 상태별 건수 |
| DELETE | `/calls/{orderId}` | 콜 취소 |
| POST | `/calls/{orderId}/confirm-dreami` | 드리미 확정 |
| POST | `/calls/{orderId}/reject-dreami` | 드리미 거절 |
| GET | `/dashboard` | 절감 리포트 |

에러코드는 커스텀 `@ApiErrorCodes`로 엔드포인트마다 명시한다 — `ORDER_NOT_FOUND`(404), `NOT_ORDER_OWNER`(403), `CANNOT_CANCEL_AFTER_PICKUP`(409), `SAME_ORIGIN_DESTINATION`(400), `TOO_MANY_ACTIVE_ORDERS`(409), `NO_DREAMI_TO_CONFIRM`(409), `INVALID_DREAMI_CONFIRMATION`(409), `INVALID_CURSOR`(400) 등.

`/expected-value`만 `@LoginUser`를 받지 않는다. `@PublicApi`도 없으므로 **로그인은 필요하지만 사용자를 식별하지는 않는** 애매한 상태다.

## 3. 요금 산정

```
BASE_SECTION  = 1500 m   기본 구간
UNIT_DISTANCE =  100 m   과금 단위
BASE_RATE     =  100 원  기본 구간 100m당
BASE_FEE      = 3000 원  기본요금
OVER_RATE     =  160 원  초과 구간 100m당
```

```
baseDistance = min(totalDistance, 1500)
overDistance = max(totalDistance - 1500, 0)
price  = (baseDistance / 100) * 100 + (overDistance / 100) * 160 + 3000
amount = round(price × ItemCd 배율 × ItemSizeCd 배율)
eta    = ceil(totalTime_sec / 60)
```

정수 나눗셈이라 100m 미만은 버린다. 거리·시간은 카카오 도보 경로의 실측값이다.

| `ItemCd` | 배율 | | `ItemSizeCd` | 배율 |
|---|---|---|---|---|
| DOCUMENT | 1.0 | | S | 1.0 |
| SAMPLE | 1.3 | | M | 1.5 |
| PACKAGE | 1.5 | | | |
| ETC | 1.2 | | | |

예: 5km / 900초 / DOCUMENT / S → `1500 + 5600 + 3000 = 10100원`, ETA 15분.

**클라이언트가 보낸 요금과 시간은 신뢰하지 않는다**(`b615cf13`). 견적과 주문 접수가 같은 `calculatePrice`를 공유해 서버가 항상 다시 계산한다.

`ItemSizeCd`는 **ORDERS에 저장되지 않는다**(`38e411d2`) — 요금 계산에만 쓴다. 이 결정이 4절 절감액 산식을 뒤틀어 놓는다.

## 4. 절감액 대시보드

`GET /dashboard`가 반환하는 것: 누적 완료 건수, 누적 절감액, 이번 달 건수·결제액·시장 환산액·절감액, 전월 대비 증감률, 최근 6개월 절감액 추이.

### 4.1 세 번의 진화

**1단계 — 대시보드 API 신설 (`d2d9a93b`)**
그전까지 프론트는 주문 목록 첫 페이지 길이(최대 20건)를 총 이용 건수로 쓰고 있었고, 절감 금액은 그냥 문자열 리터럴이었다. 서버 집계로 옮기면서 시장 단가를 **건당 5,800원**으로 잡았다.

**2단계 — 단가 갱신 5,800 → 10,000원 (`f9755e07`)**
문제는 **5,800원에 근거가 없었다**는 것이다. 주석 한 줄이 전부였고 도입 커밋에도 출처가 없었다. 실제 서울 오토바이 퀵의 3km 이내 기본요금은 8,000~12,000원이라 5,800원은 시장가를 과소평가하고 있었다. 조사 범위의 중앙값 10,000원을 채택하고 근거와 출처를 주석에 남겼다. 동시에 `marketUnitPrice`와 `thisMonthPaidAmount`를 응답에 추가했는데, **프론트에 단가를 하드코딩하는 것보다 서버가 내리는 쪽이 단일 진실 소스를 지킨다**는 판단이다.

**3단계 — 건수 + 거리 기준으로 재계산 (`13b828a9`, 현행)**
건당 고정 10,000원을 시장가로 보면, 콜 단가가 10,000원을 넘는 순간 절감액이 음수가 되어 0으로 클램프되고 **대시보드에서 아예 사라진다**. 장거리 주문일수록 절감 효과가 큰데 화면에는 0으로 나오는 셈이다. 그래서 거리를 반영하는 현행 산식으로 바꿨다.

### 4.2 현행 산식

```
MARKET_BASE_FEE     = 11000 원  시장 퀵 건당 기본요금(기본 구간 이내)
MARKET_BASE_SECTION =  3000 m   시장 퀵 기본 구간
MARKET_OVER_RATE    =   240 원  시장 퀵 초과 구간 100m당
```

```
marketAmountOf(itemCd, count, overDistance)
  = round( (count × 11000 + (overDistance / 100) × 240) × ItemCd 배율 )

totalMarketAmount = Σ(물건 유형별) marketAmountOf(...)
totalSavedAmount  = max(0, totalMarketAmount - Σ 결제액)

growthPercent = lastMonthSaved == 0 ? 0
              : round((thisMonthSaved - lastMonthSaved) × 100.0 / lastMonthSaved)
recentSixMonths = [thisMonth-5 … thisMonth] 오름차순, 기록 없는 달은 0
```

근거 주석: 서울 오토바이 퀵 3km 이내 기본요금 8,000~12,000원, 초과 구간 km당 2,000원대(2026-08 조사, gosuquick.com / 1600-7324.com / ssanquick.com / silverquick.kr).

### 4.3 `MARKET_OVER_RATE = 240`이 우리 요율의 1.5배인 이유

우리 초과 요율은 160원/100m인데 시장 환산은 240원/100m — 정확히 1.5배다. 이건 시장 조사 결과가 아니라 **보정값**이다.

3절에서 `ItemSizeCd`를 ORDERS에 저장하지 않기로 했기 때문에, 집계 시점에는 그 주문이 S였는지 M이었는지 알 수 없다. 실제 결제액에는 크기 배율(최대 1.5)이 곱해져 있는데 시장 환산액에는 반영할 수가 없다. 그대로 두면 M 주문에서 절감액이 음수로 뒤집힌다. 그래서 **크기 배율만큼을 초과 요율에 미리 얹었다**.

마진이 얇다는 게 문제다. 최악 조합(10km, PACKAGE × M)을 테스트가 고정하고 있다.

```
결제액  = 18100 × 1.5 × 1.5 = 40725
시장환산 = (11000 + 7000/100 × 240) × 1.5 = 41700
절감액  =  975원
```

975원이면 상수를 조금만 건드려도 음수로 넘어간다. **근본 해법은 `ORDERS.item_size_cd` 컬럼을 추가하는 것**이고, 지금 방식은 DDL 변경을 피한 우회다.

### 4.4 기준 시각이 둘로 갈린다

| 값 | 기준 |
|---|---|
| 누적(`completedCount`, `totalSavedAmount`) | `ORDERS.order_cd = COMPLETED` |
| 월별(추이, 이번 달 값 전부) | `DELIVERY.delivery_cd = DELIVERED` + `DELIVERY.delivery_end_dtm` |

이유는 단순하다 — **ORDERS에 완료 시각 컬럼이 없어서 월 경계를 그을 수 없다.** 부작용도 알고 있다: 배달은 끝났는데 주문이 아직 완료 처리되지 않은 구간에서 두 값이 일시적으로 어긋난다. 수용된 트레이드오프다.

### 4.5 쿼리

대시보드는 **쿼리 2회 고정**이다(누적 1 + 월별 1). 6개월 루프가 있어도 추가 쿼리가 없다 — `e196815a`에서 별도 카운트 쿼리를 월별 집계 하나로 흡수했다.

누적(`OrderRepository`):

```sql
SELECT new CompletedSavingAggregate(
  o.itemCd, COUNT(o),
  COALESCE(SUM(CASE WHEN o.deliveryDistance > :baseSection
                    THEN o.deliveryDistance - :baseSection ELSE 0 END), 0),
  COALESCE(SUM(o.deliveryAmount), 0))
FROM Orders o
WHERE o.boormiId = :boormiId AND o.orderCd = COMPLETED
GROUP BY o.itemCd
```

**배율표를 SQL에 복제하지 않는다.** item_cd로 그룹핑만 하고 배율은 서비스에서 곱한다 — `ItemCd` enum이 배율의 단일 진실 소스로 남는다. `COALESCE`는 `delivery_distance`가 NULL인 옛 주문을 0으로 취급하기 위한 것이다.

월별(`DeliveryRepository`)은 같은 형태에 `YEAR/MONTH` 그룹핑이 추가되고, **`Delivery`에 연관관계 매핑이 없어 `order_id` 세타 조인**으로 붙인다. 기간은 `[이번달-5월 1일 00:00, 다음달 1일 00:00)` 반개구간.

목록 조회는 엔티티가 아니라 `OrderSummaryDto`로 생성자 투영해 `route_path` 같은 대형 컬럼을 아예 읽지 않는다. 커서는 `(deliveryRequestDtm, orderId)` 복합이고, `IX_ORDERS_BOORMI_LIST(boormi_id, delivery_request_dtm DESC, order_id DESC)`가 정확히 이 정렬을 커버한다(FK보다 먼저 만들어 FK 자동 인덱스와 중복되지 않게 했다).

## 5. 주문 접수 — 트랜잭션을 어디에 그을 것인가

`subscribeOrder`에는 **`@Transactional`을 붙이면 안 된다**(`ee0e37a2`, 이슈 #437). 이 메서드는 카카오 API를 3번 부른다(출발지 지오코딩, 도착지 지오코딩, 도보 길찾기). 트랜잭션 안이면 외부 응답을 기다리는 내내 DB 커넥션을 붙들어 Hikari 풀이 마른다.

DB 쓰기는 `OrderPlacementService.place`가 별도 트랜잭션으로 맡는다. **별도 빈이어야 하는 이유가 두 개**다.

- `BoormiService`의 private 메서드로 두면 자기 호출이라 프록시를 안 타고 트랜잭션이 안 걸린다
- `OrderService`에 합치면 MatchingService → DeliveryService → OrderService 순환 의존이 생긴다

접수 순서:

1. `countActiveOrders >= 5`면 `TOO_MANY_ACTIVE_ORDERS` — 카카오를 부르기 전에 걸러낸다
2. 지오코딩 2회 → 직선거리 50m 미만이면 `SAME_ORIGIN_DESTINATION`. **카카오 호출 전 가드**다(같은 좌표에는 경로를 못 준다)
3. 길찾기 → 서버 재계산 + 폴리라인용 경로 JSON 직렬화. **직렬화가 실패해도 경로 없이 주문을 진행**한다(로그만 남기고 null)
4. `orderPlacementService.place(orders, amount)` → 주문 저장 → 결제 → 진행 중 매칭방 있으면 `CONFLICT` → `MatchingStartRequestedEvent` 발행

이벤트 발행은 `place` **안**에 있어야 한다. AFTER_COMMIT 리스너는 트랜잭션 밖에서 발행하면 조용히 실행되지 않는다.

`@Transactional` 부재는 리플렉션으로 단언하는 회귀 테스트가 지키고 있다 — 단위 테스트에는 프록시가 없어 동작으로는 관찰할 수 없으니 어노테이션 자체를 검사한다.

## 6. 취소와 더블 컨펌

**취소** (`unsubscribeOrder`) — `getOrderForUpdate`로 **비관적 쓰기 락**을 잡고 시작한다. 더블클릭·재시도로 동시 취소가 들어오면 취소 이력이 두 줄 쌓이기 때문이다. 소유자 확인 후 상태가 `MATCHING` 또는 `PENDING_BOORMI_CONFIRMATION`이어야 하고, 그다음 `orderService.cancel` → `paymentService.refundByPoint` → `OrderCancelledByBoormiEvent` 발행.

**확정** (`confirmDreami`) — 상태가 `PENDING_BOORMI_CONFIRMATION`이어야 하고, 요청의 `offerId`가 `order.pendingOfferId`와 **일치해야** 한다(아니면 `NO_DREAMI_TO_CONFIRM`). 이 대조가 재요청 멱등성과 오퍼 소유권을 동시에 해결한다. 통과하면 `IN_PROGRESS`로 전이하고 `Matching`을 accepted로 저장한다.

**거절** (`rejectDreami`) — 같은 락·대조를 거쳐 `MATCHING`으로 되돌리고 `dreamiId`/`pendingOfferId`를 해제한다.

세 경로 모두 매칭 엔진 제출을 **커밋 후 이벤트로 미룬다**. 이유가 각각 다르다.

- 취소: 커밋 전에 제출하면 롤백 시 인메모리 방만 종료돼 영영 재매칭이 안 된다
- 거절: 엔진이 즉시 재오퍼를 돌리는데, 다른 드리미가 이미 커밋한 `PENDING_BOORMI_CONFIRMATION`을 이 트랜잭션의 MATCHING 복귀가 덮어써 주문이 고착된다

## 7. 프론트엔드 연동

| 화면 | 호출 |
|---|---|
| `pages/home/ui/SenderPanel.tsx` | `getBoormiDashboard()` → 완료 건수·누적 절감액. **보조 지표라 실패해도 화면을 막지 않고 0으로 둔다**(`.catch(() => {})`) |
| `pages/earnings/ui/SenderSavings.tsx` | 절감 리포트 전체. hero 카드 + 6개월 BarChart + **"N건 시장 환산 X원 − 결제 Y원 = Z원"** 산술식을 그대로 렌더 |
| `pages/request-create/**` | `expectedValue()` 견적, 주문 요청 조립(itemCd/itemSizeCd) |
| `shared/store/boormiOrderStore.ts` | 목록/등록/취소/상태별 건수 |
| `shared/store/matchingStore.ts` | `confirmDreami` / `rejectDreami` |
| `pages/matching/ui/MatchingScreen.tsx` | `getBoormiOrder(orderId)` — 딥링크·새로고침 진입 |

주의할 결합 지점 두 개:

- `pages/earnings/ui/savingsNote.ts`가 **요금 상수(3000 / 1500 / 100)를 하드코딩**한다. 주석에 "백엔드 `BoormiService.calculatePrice`에서 가져온 값이라 요금 정책이 바뀌면 여기도 같이 고쳐야 한다"고 적어 뒀다. 반대로 시장 환산액은 서버가 내려주므로 하드코딩하지 않는다.
- `SenderSavings`가 `recentSixMonths`의 마지막 원소를 이번 달로 쓴다 — 서버의 오름차순 정렬 계약에 의존한다.

## 8. 남은 리스크

1. **필드/컬럼 이름 불일치** — 엔티티 `isDreamiActivate` ↔ 컬럼 `is_dreami_activated`. DDL은 `BOOLEAN NULL`인데 엔티티 매핑은 `nullable = false`다.
2. **`name` 중복 검사가 애플리케이션 레벨만** — DDL에 UNIQUE가 없고 `existsByName`으로만 막는다. 동시 가입 경합에 취약하다.
3. **절감액 마진 975원** (4.3). `ORDERS.item_size_cd` 추가가 근본 해법.
4. **`MAX_ACTIVE_ORDERS = 5`가 트랜잭션 밖 검사** — 동시 요청은 함께 통과할 수 있다. 코드 주석이 한계를 자인하고 있고, 한도 초과 사용자를 카카오 호출 전에 거르는 이점을 더 쳤다.
5. **역방향 패키지 의존** — `ItemCd`가 boormi 패키지에 있는데 order/delivery의 집계 DTO가 import한다. 위치상 order 도메인이 더 적절해 보인다.

## 9. 다른 도메인과의 연결

- **[User 도메인](../user/overview.md)** — 계정 CRUD, `Boormi` 엔티티를 읽고 쓰는 주체.
- **[Payment 도메인](../payment/overview.md)** — `OrderPlacementService`가 결제를, `unsubscribeOrder`가 환불을 호출한다.
- **[Address 도메인](../../hyeonseo/address/overview.md)** — `CoordinatesService`/`DirectionsService`를 직접 주입받아 쓴다(`AddressService`가 아니다).
- **[Dreami 도메인](../../hyeonseo/dreami/overview.md)** — PK 공유 구조, `DreamiActivationChecker`.
- **[Matching 도메인](../../donghyeok/matching/matching-domain-design-philosophy.md)** — 주문 접수/취소/확정/거절이 전부 커밋 후 이벤트로 매칭 엔진에 닿는다.
