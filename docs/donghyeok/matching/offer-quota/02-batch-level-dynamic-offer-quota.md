정# 2. 드리미 수급 기반 dynamic offer quota 추가

`resolveMaxConcurrentOffers()`가 설정값을 그대로 반환하던 자리에, 배치 시점의 드리미/주문
비율로 quota를 다시 계산하는 `DYNAMIC` 모드를 추가했다. `matching.offer-quota-mode`로
`FIXED`/`DYNAMIC`을 선택하며, 기본값은 `FIXED`라 기존 동작은 그대로 유지된다.

## 무엇을

- `OfferQuotaMode` enum(`FIXED`, `DYNAMIC`) 추가.
- `MatchingPolicyProperties`에 `offerQuotaMode` 필드 추가, `matching.offer-quota-mode=${OFFER_QUOTA_MODE:FIXED}`
  설정 추가.
- `MatchingAssignmentProblemAssembler.resolveMaxConcurrentOffers()`가 `orderCount`/`dreamiCount`를
  받아 모드에 따라 분기하도록 변경. `FIXED`는 기존처럼 설정값을 그대로 쓰고, `DYNAMIC`은
  `calculateDynamicQuota(orderCount, dreamiCount)`가 대기 드리미 수를 대기 주문 수로 나눠 올림한
  값을 [1, 5] 범위로 잘라 반환한다. 주문이 0이면 0으로 나누기를 피해 1을 반환한다.
- 값은 배치당 한 번만 계산해 그 배치의 모든 주문에 동일하게 적용한다(주문별로 다른 quota를 주지 않음).

## 왜

주문이 몰리고 드리미가 적을 때 고정 quota(3)를 그대로 쓰면 각 주문이 적은 드리미 풀을 나눠
경쟁하게 되어 일부 주문은 오퍼조차 못 받는다. 드리미가 넘칠 때는 반대로 quota를 낮춰도 될
여유가 있다. `DYNAMIC` 모드는 이 수급 불균형에 맞춰 배치마다 quota를 다시 산정한다.

## 테스트

`MatchingAssignmentProblemAssemblerTest`에 `DYNAMIC` 케이스(3주문/7드리미 → 3, 3주문/3드리미 →
1, 드리미 0명 → 최소 1, 비율 5 초과 → 최대 5)와 `FIXED` 회귀 케이스를 추가했고,
`MatchingPolicyConfigurationTest`에 `offer-quota-mode` 바인딩 확인 테스트를 추가했다.
직접 `MatchingPolicyProperties`를 생성하던 기존 테스트(`MatchingMicroBatchIntegrationTest`)에는
`FIXED`를 넣어 기존 의미를 유지했다.
