# 매칭 단계 동시 취소 — 이중환불 분석

> 분석일: 2026-08-16 / 대상 브랜치: `develop` (e0d9aa01)
> 코드 수정 없이 분석만 수행함.

## 결론: 예, 이중환불이 가능합니다

매칭 단계 취소 경로에 **행 잠금이 하나도 없고**, 환불 멱등 가드가 **잠기지 않은 행을 읽고 판단**하기 때문입니다.

## 경로

`BoormiController:87` → `BoormiService.unsubscribeOrder` (`BoormiService.java:144-163`)

```java
Orders order = orderService.getOrder(orderId);   // findById — 락 없음
... 소유권/상태 검사 (MATCHING | PENDING_BOORMI_CONFIRMATION)
orderService.cancel(order, CancelerCd.BOORMI);   // 상태 전이 + CANCEL insert
paymentService.refundByPoint(orderId);           // 환불
```

`PaymentService.refundByPoint` (`PaymentService.java:74-89`)

```java
PointTx pointTx = pointTxRepository.findByOrderIdAndType(...);  // ← 락 없음
if (!pointTx.markRefundedFull()) return;                        // ← check-then-act
PointWallet w = pointWalletRepository.findByIdForUpdate(...);   // ← 락은 '판단 이후'에 잡힘
w.refund(pointTx.getAmount());
```

## 레이스 시나리오 (더블클릭 / 재시도 / 다중 인스턴스)

| | TX1 | TX2 |
|---|---|---|
| 1 | `SELECT ORDERS` → MATCHING (스냅샷 확정) | `SELECT ORDERS` → MATCHING |
| 2 | 상태 검사 통과 | 상태 검사 통과 |
| 3 | `SELECT POINT_TX` → PENDING | `SELECT POINT_TX` → PENDING |
| 4 | `markRefundedFull()` → **true** | `markRefundedFull()` → **true** |
| 5 | `POINT_WALLET` FOR UPDATE 획득, +4000 | 락 대기 |
| 6 | commit (락 해제) | 락 획득 → **현재값(이미 +4000된 잔액)** 읽고 다시 +4000 |
| 7 | | commit |

결과: **포인트 8000 환급**, `POINT_LEDGERS` 에 +4000 두 줄, `CANCEL` 두 줄.

## 왜 기존 방어책이 안 통하나

- **`markRefundedFull()` 멱등 가드**: 단일 트랜잭션 재실행에는 유효하지만, 두 트랜잭션이 같은 PENDING 스냅샷을 동시에 읽으면 둘 다 `true` 를 반환합니다. 상태 전이 판단이 락 밖에 있는 게 핵심입니다.
- **`findByIdForUpdate` 지갑 락**: 잠금 순서가 **판단 이후**라, 직렬화되는 건 `amount +=` 산술뿐입니다. 두 번째 트랜잭션은 최신 잔액을 읽고 **정상적으로 한 번 더 더합니다**. 락이 오히려 lost update 를 막아주는 바람에 두 번 다 반영됩니다.
- **`UQ_POINT_TX_ORDER_TYPE`** (`sql/sym-boorm-ddl.sql:365`): 결제(INSERT)는 막지만 환불은 새 행이 아니라 기존 행의 **UPDATE** 라 제약이 걸리지 않습니다.
- **ORDERS 행 락**: `unsubscribeOrder` 는 `orderService.getOrder` → `findById`(락 없음)를 씁니다. 드리미 수락 경로용으로 만들어 둔 `OrderRepository.findByOrderId`(`PESSIMISTIC_WRITE`, `OrderRepository.java:39`)를 이 경로는 쓰지 않습니다. 설령 ORDERS UPDATE 가 flush 되며 락이 걸려도, 그건 커밋 시점이고 `POINT_TX` 비잠금 읽기는 여전히 옛 스냅샷을 봅니다.
- **`@Version`**: 프로젝트 전체에 없습니다. 낙관적 락도 없습니다.

## 격리 수준 때문에 로컬에서 안 잡힙니다

운영 MySQL 기본값 **REPEATABLE READ** 에서는 TX2 의 스냅샷이 첫 읽기(1단계)에 고정되므로, TX1 이 커밋한 뒤에도 TX2 는 `POINT_TX.status = PENDING` 을 계속 봅니다. 창(window)이 **TX2 시작 ~ TX2 의 지갑 락 획득** 전체로 넓습니다.

로컬 H2 기본값은 READ COMMITTED 라, 3단계가 TX1 커밋 이후면 `REFUNDED_FULL` 을 읽어 가드가 걸립니다 — 창이 훨씬 좁아 재현이 어렵습니다. DDL 대소문자 이슈와 같은 "운영에서만 터지는" 부류입니다.

## 부수 문제 (같은 레이스에서 함께 발생)

`CANCEL` 테이블에 `UNIQUE(order_id)` 가 없습니다 — DDL 은 `PK_CANCEL(cancel_id)` 만 있고(`sql/sym-boorm-ddl.sql:373`), 정작 `Cancel.java:29` 주석에 *"동시 취소 중복 삽입 방지를 위해 DB에 UNIQUE(order_id) 제약 필요"* 라고 이미 적혀 있습니다. 이 제약이 있었다면 두 번째 트랜잭션이 커밋에서 터져 환불까지 롤백돼 **우연히** 이중환불도 막혔을 겁니다.

## 참고: 인접하지만 다른 문제

`unsubscribeOrder` 와 `confirmDreami` 동시 실행은 이중환불이 아니라 **주문 상태 lost update** 입니다(취소된 주문이 IN_PROGRESS 로 덮이거나 그 반대). 이후 배달 취소가 `refundByPoint` 를 다시 불러도 그때는 `markRefundedFull()` 이 `false` 를 반환해 환불은 한 번만 나갑니다.

## 적용한 수정 (`fix/471`)

비관적 락만 추가했다. 스키마 변경(`@Version`, UNIQUE 제약)은 하지 않았다.

### 1. `POINT_TX` 잠금 읽기 — 이중환불 차단

`PointTxRepository.findByOrderIdAndTypeForUpdate` (`PESSIMISTIC_WRITE`)를 추가하고 `PaymentService.refundByPoint` 가 이걸 쓰도록 바꿨다. 상태 전이 판단(`markRefundedFull`)이 락 **안**에서 일어나므로, TX2 는 TX1 이 커밋할 때까지 대기했다가 잠금 읽기(current read)로 `REFUNDED_FULL` 을 보고 조기 반환한다.

`refundByPoint` 를 부르는 모든 경로(매칭 단계 취소 + `DeliveryService` 픽업 취소 3곳)가 함께 보호된다.

### 2. `ORDERS` 잠금 읽기 — 중복 취소 이력 차단

`OrderService.getOrderForUpdate` 를 추가하고(기존 `OrderRepository.findByOrderId`(`PESSIMISTIC_WRITE`) 재사용) `BoormiService.unsubscribeOrder` 가 이걸 쓰도록 바꿨다. 상태 검사부터 취소·환불까지가 주문 단위로 직렬화되어, 두 번째 트랜잭션은 최신 `CANCELLED` 를 읽고 `CANNOT_CANCEL_AFTER_PICKUP` 으로 걸린다. `CANCEL` 행도 한 줄만 쌓인다.

`getOrder`(`readOnly`, 락 없음)는 조회 전용 경로용으로 그대로 뒀다 — FOR UPDATE 는 읽기 전용 트랜잭션에서 못 쓴다.

### 잠금 순서

`ORDERS → POINT_TX → POINT_WALLET`, 배달 경로는 `DELIVERY → POINT_TX → POINT_WALLET`. 역순으로 잡는 경로가 없어 데드락은 생기지 않는다.

## 남은 것 (이번 범위 밖)

- `CANCEL` 의 `UNIQUE(order_id)` 제약은 여전히 없다. 위 락으로 애플리케이션 레벨에서는 막히지만, DB 레벨 최종 방어선으로는 따로 값어치가 있다(`Cancel.java:29` 주석 참고).
- `PaymentService.settleOrder` 도 `markPaid()` 로 같은 check-then-act 패턴을 쓰며 잠기지 않은 조회를 한다. 동시 배달 완료 처리에서 이중 정산 가능성이 있으나 이번 이슈 범위가 아니라 손대지 않았다.
- `unsubscribeOrder` 와 `confirmDreami`/`rejectDreami` 사이의 주문 상태 lost update 는 남아 있다(`confirmDreami` 는 아직 잠그지 않은 `getOrder` 사용). 이중환불은 아니다.
