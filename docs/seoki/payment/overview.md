# Payment 도메인

지갑(포인트·머니), 충전·결제·환불·정산·전환을 담당한다.

설계의 중심 원칙 하나: **Tx = 거래의 현재 상태(행 하나), Ledger = append-only 변동 로그.**
환불도 정산도 새 거래 행을 만들지 않고 **원본 `PointTx`의 status를 전이**시킨다. 잔액이 실제로 얼마씩 오르내렸는지는 Ledger가 부호 있는 금액과 `balanceAfter`로 남긴다. 거래 1건에 원장 여러 줄이 붙는 구조다.

## 1. 지갑 3층 구조

| 테이블 | 역할 |
|---|---|
| `WALLET` | 회원(`boormi_id`)당 1행. 지갑의 정체성만 갖는다 |
| `POINT_WALLET` | `wallet_id` 공유 PK. `amount` |
| `MONEY_WALLET` | `wallet_id` 공유 PK. `amount`, `pending_amount` |

세 행은 **회원가입 시 잔액 0으로 함께 만들어진다** — `UserService.signup`이 같은 트랜잭션에서 `WalletService.createWallet`을 부른다. FK 때문에 `WALLET`을 먼저 저장한 뒤 나머지 둘이 그 `wallet_id`를 물고 붙는다.

지갑을 못 찾는 `WALLET_NOT_FOUND`(404)는 실질적으로 **지갑 생성 도입 이전에 가입한 계정**에서만 난다.

## 2. 포인트 vs 머니

| | 포인트 | 머니 |
|---|---|---|
| 누가 갖나 | 모든 회원 | 실질적으로 드리미(테이블은 전원 보유) |
| 어디서 들어오나 | 충전(`CHARGE`), 전환(`EXCHANGE_IN`) | 정산(`SETTLEMENT`) |
| 어디로 나가나 | 콜 결제(`PAYMENT`) | 포인트 전환(`EXCHANGE_OUT`) |
| 비율 | 1원 = 1P | 1머니 = 1P (수수료 0) |

전체 루프:

```
PG 충전 → 포인트 적립
        → 콜 결제 (포인트 차감, PointTx = PENDING)
        → 배달 완료 → PointTx = PAID + 드리미 머니 정산 (MoneyTx = SETTLED)
        → 머니 → 포인트 전환 (1:1)
        → 출금 (미구현)
```

주문이 취소되면 결제 지점에서 되감긴다 — `PointTx = REFUNDED_FULL` + 포인트 원복.

## 3. API

base: `/api/v1/wallet`. 셋 다 로그인 필수(`@PublicApi` 없음).

| Method | Path | 설명 |
|---|---|---|
| GET | `/` | 포인트·머니 잔액 + 최근 거래 20건 |
| POST | `/point/charge` | 포인트 충전 |
| POST | `/exchange` | 머니 → 포인트 전환 |

**결제·환불·정산에는 엔드포인트가 없다.** `PaymentService`는 다른 도메인(`OrderPlacementService`, `BoormiService`, `DeliveryService`)이 서비스 호출로만 쓴다.

| 에러코드 | 상태 |
|---|---|
| `WALLET_NOT_FOUND` | 404 |
| `INSUFFICIENT_POINT` | 400 |
| `ALREADY_PAID` | 409 |
| `PAYMENT_NOT_FOUND` | 404 |
| `INSUFFICIENT_MONEY` | 400 |

`getWallet`은 두 원장을 각각 최신 20건씩 읽어 시간 역순으로 합친 뒤 상위 20건만 남긴다. 합쳐서 20건이면 되므로 각각 20건씩만 읽으면 충분하다.

## 4. PENDING — 결제는 에스크로다

포인트는 결제 시점에 **바로 빠지지만** 그 거래는 `PENDING`으로 남는다(`dd23446e`).

> 배달 콜 결제는 포인트가 빠져도 배달이 끝나기 전까지는 지급이 확정된 게 아니므로 PENDING으로 시작해 배달 완료 시 PAID로 전이한다.

`PointTx.create()`(=PAID로 시작)와 `createPending()`이 나뉜 이유가 이것이다. **충전과 전환은 PENDING을 거치지 않는다** — 받는 순간 거래가 끝나기 때문이다. 콜 결제만 "차감됐지만 확정 안 됨" 구간을 갖는다.

| enum | 값 |
|---|---|
| `PointTxTypeCd` | CHARGE, PAYMENT, REFUND, EXCHANGE_IN |
| `PointTxStatusCd` | PENDING, PAID, REFUNDED_PARTIAL, REFUNDED_FULL |
| `MoneyTxTypeCd` | SETTLEMENT, REVERSAL, CLAIM_ADJUSTMENT, EXCHANGE_OUT |
| `MoneyTxStatusCd` | PENDING, SETTLED, REJECTED |

## 5. 세 가지 흐름

### 5.1 결제 — `payWithPoint(boormiId, orderId, amount)`

```
1. 포인트 지갑을 비관적 쓰기 락으로 조회      ← 락이 먼저
2. (orderId, PAYMENT) 거래가 이미 있으면 ALREADY_PAID
3. PointTx.createPending(PAYMENT) 저장
4. pointWallet.deduct(amount)               ← 잔액 부족이면 INSUFFICIENT_POINT, 전체 롤백
5. PointLedger.create(..., -amount, 차감 후 잔액)
```

애플리케이션 중복 검사를 경합으로 동시에 통과하더라도 **`UQ_POINT_TX_ORDER_TYPE`(order_id, type) 유니크 제약**이 두 번째 저장을 막는다. 검사는 사용자에게 깔끔한 409를 주기 위한 것이고, 실제 마지막 방어선은 DB 제약이다.

### 5.2 환불 — `refundByPoint(orderId)`

```
1. (orderId, PAYMENT) 거래를 findByOrderIdAndTypeForUpdate 로 조회   ← 비관적 락
2. markRefundedFull() 이 false 면(이미 환불) 그대로 return
3. 포인트 지갑을 락으로 조회
4. pointWallet.refund(amount)
5. PointLedger.create(..., +amount, 복구 후 잔액)
```

**전액 환불만 있다.** `REFUNDED_PARTIAL`은 enum에만 있고 쓰이지 않는다.

호출부 4곳:

| 호출부 | 취소 주체 |
|---|---|
| `BoormiService.unsubscribeOrder` | 부르미 (매칭 중 / 확정 대기 중) |
| `DeliveryService.cancelByDreami` | 드리미 (픽업 중) |
| `DeliveryService.cancelByBoormi` | 부르미 (픽업 중) |
| `DeliveryService.cancelByAdmin` | 관리자 (픽업 중) |

셋 다 "SSE 알림 전에 DB 작업을 끝낸다"는 순서를 지킨다.

### 5.3 정산 — `settleOrder(orderId, dreamiId)`

```
1. (orderId, PAYMENT) 거래 조회      ← 락 없음
2. markPaid() 가 false 면(이미 확정 또는 환불됨) 그대로 return
3. 드리미 머니 지갑 락 조회
4. moneyWallet.add(amount)
5. MoneyTx.createSettled(SETTLEMENT) + MoneyLedger +amount
```

**수수료 0%** — 부르미가 낸 금액이 그대로 드리미 몫이 된다.

드리미 지갑을 `walletRepository.findByBoormiId(dreamiId)`로 찾는데, 이건 [Boormi 도메인](../boormi/overview.md)의 **PK 공유 전제**(드리미 UUID = 부르미 UUID)에 기대는 것이다. 그 전제가 깨지면 여기가 `WALLET_NOT_FOUND`로 터진다.

## 6. 동시성 — 락이 상태 판단보다 앞서야 한다

### 6.1 포인트 이중 환불 (`ea44c59c`)

환불이 원래 `findByOrderIdAndType`(락 없음)으로 거래를 읽었다. 동시 취소 두 건이 **같은 PENDING 스냅샷**을 보면 `markRefundedFull()`이 양쪽에서 true를 반환하고, 포인트가 두 번 복구된다.

지갑 락으로는 못 막는다. **지갑 락은 잔액 산술만 직렬화할 뿐 "이미 환불됐는지"라는 check-then-act를 지켜주지 못한다.** 그래서 락을 상태 판단 앞으로 당겼다 — `findByOrderIdAndTypeForUpdate`로 거래 행 자체를 잠그고 시작한다.

회귀 테스트가 이 순서를 고정한다.

```java
then(pointTxRepository).should().findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT);
then(pointTxRepository).should(never()).findByOrderIdAndType(any(), any());
```

락 없는 조회를 **쓰지 않는다는 것**까지 단언한다. 단위 테스트에서는 실제 락 동작을 관찰할 수 없으니 "어느 메서드를 불렀나"로 대신 지킨다.

### 6.2 결제·정산의 비대칭

- **결제**: 지갑 락이 먼저, 그다음 중복 검사. 최후 방어선이 유니크 제약이라 이 순서로 충분하다.
- **환불**: 거래 락이 먼저 (6.1).
- **정산**: 거래를 **락 없이** 읽는다. `settleOrder`는 배달 완료라는 단일 경로에서만 불리므로 동시 호출 자체가 상정되지 않는다. 환불과 경합하면 `markPaid()`가 false를 반환해 조기 return되는 쪽으로 흐른다. 다만 이 비대칭은 **의도된 것이라는 근거가 코드에 명시돼 있지 않다** — 환불 쪽만 락을 갖는 게 자연스러워 보이지 않는다면 여기가 검토 지점이다.

### 6.3 전환의 락 순서 고정

`exchangeMoneyToPoint`는 **두 지갑을 모두 잠근다.** 그래서 순서를 **항상 머니 → 포인트**로 고정한다. 다른 트랜잭션이 반대 순서로 잡으면 데드락이 나기 때문이다. 현재 반대 순서로 두 지갑을 함께 잠그는 코드는 없지만, 순서를 코드와 주석으로 못 박아 뒀다.

전환 한 번에 남는 행: `MoneyTx(EXCHANGE_OUT)` + `MoneyLedger(-amount)` + `PointTx(EXCHANGE_IN)` + `PointLedger(+amount)` + 둘을 짝지어 주는 `Exchange` 1행.

## 7. 트랜잭션 경계

`PaymentService`의 메서드는 전부 `@Transactional`이지만, **실제로는 항상 상위 트랜잭션에 REQUIRED로 참여한다.**

| 상위 | payment 호출 |
|---|---|
| `OrderPlacementService.place` | `payWithPoint` |
| `BoormiService.unsubscribeOrder` | `refundByPoint` |
| `DeliveryService.cancelBy*` | `refundByPoint` |
| `DeliveryService`(배달 완료) | `settleOrder` |

주문 저장과 결제가 한 트랜잭션이므로 **"주문은 생겼는데 결제가 안 된" 상태가 존재할 수 없다.**

**payment는 다른 도메인에 의존하지 않는다.** UUID(`boormiId`, `orderId`, `dreamiId`)만 받는다. 의존은 전부 단방향으로 payment를 향한다. `WalletService`가 `UserService`에 주입되는 것도 payment 입장에서는 밖에서 들어오는 방향이다.

## 8. 프론트엔드 연동

| 화면 | 역할 |
|---|---|
| `pages/wallet/ui/WalletScreen.tsx` | 잔액 + 최근 내역 |
| `pages/point-charge/ui/ChargeForm.tsx` | 포인트 충전 |
| `pages/point-charge/ui/ConvertForm.tsx` | 머니 → 포인트 전환 |
| `pages/request-create/ui/StepPayment.tsx` | 콜 결제 직전 잔액 확인 |

`shared/store/walletStore.ts`가 넷을 다 받친다. 설계 원칙이 주석에 적혀 있다 — **"잔액의 진실은 서버가 가지므로 충전·전환도 낙관적 갱신 없이 응답으로 돌아온 지갑 한 벌로 상태를 통째로 교체한다."** 충전/전환 API가 갱신된 `WalletDto`를 통째로 반환하는 것도 이 계약에 맞춘 것이다.

## 9. 문서-코드 불일치와 미구현

정직하게 남긴다.

1. **`MoneyWallet`은 전원에게 만들어진다.** `PaymentService.lockMoneyWallet`의 Javadoc은 "머니 지갑은 드리미 등록 때 만들어진다"고 하지만, 실제로는 `createWallet`이 회원가입 때 세 행을 다 만든다. 주석이 틀렸다.
2. **`MONEY_WALLET.pending_amount`가 사실상 죽은 필드다.** 생성 시 0으로 넣고 `WalletDto`로 내보내는 것 외에 **0이 아닌 값이 되는 경로가 없다.** 정산 대기 금액을 담으려던 자리로 보이나 정산이 즉시 확정(`createSettled`)이라 쓸 일이 없다.
3. **미사용 enum 값** — `PointTxTypeCd.REFUND`, `PointTxStatusCd.REFUNDED_PARTIAL`, `MoneyTxTypeCd.REVERSAL`/`CLAIM_ADJUSTMENT`, `MoneyTxStatusCd.PENDING`/`REJECTED`. 환불이 새 거래(`REFUND`)가 아니라 상태 전이로 설계된 순간 `REFUND`는 갈 곳이 없어졌다.
4. **`MoneyTx.create()`는 호출되지 않는다.** Javadoc은 "상태는 항상 PENDING으로 시작"이라 하지만 실사용은 정산·전환 둘 다 `createSettled`뿐이다. 따라서 `MoneyTxStatusCd.PENDING` 행은 생기지 않는다.
5. **`PAYMENT.refund_dtm` 미사용.** 컬럼은 DDL에 있으나 채우는 코드가 없다.
6. **PG 미연동.** `chargePoint`는 결제를 항상 성공으로 보고 즉시 적립한다. `PaymentCd`(CARD / BANK_TRANSFER / TOSS_PAY)는 기록용일 뿐 분기가 없다.
7. **출금 미구현.** `SETTLEMENT_DETAILS` 테이블과 `SettlementDetailsRepository`가 있지만 **호출부가 0개다.** 드리미가 머니를 실제 현금으로 빼는 경로는 아직 없고, 포인트로 되돌리는 것만 가능하다.

## 10. 다른 도메인과의 연결

- **[Boormi 도메인](../boormi/overview.md)** — `OrderPlacementService`가 결제를, `unsubscribeOrder`가 환불을 부른다. 정산은 PK 공유 전제에 기댄다.
- **[User 도메인](../user/overview.md)** — `signup`이 지갑 3행을 만든다.
- **[Dreami 도메인](../../hyeonseo/dreami/overview.md)** — 정산 대상.
- **Delivery 도메인** — 배달 완료 시 `settleOrder`, 픽업 중 취소 3종에서 `refundByPoint`.
