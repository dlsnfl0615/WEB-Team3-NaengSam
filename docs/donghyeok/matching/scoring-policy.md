# 매칭 스코어링 정책 (MatchingScorePolicy)

기존 매칭은 대기 시간순 FIFO 하나로 고정되어 있었다. 이를 교체 가능한 정책으로 뽑아내기 위해
`MatchingScorePolicy` 인터페이스(`score(MatchingCandidate): long`, 점수가 낮을수록 우선순위가 높다)를
추가하고, 점수 계산에 필요한 원시 입력값을 `MatchingCandidate` 불변 레코드(`orderId`, `dreamiId`,
`distanceMeters`, `orderWaitingTime`, `dreamiWaitingTime`, `orderCandidateCount`,
`dreamiCandidateCount`, `previousInteraction`)로 정의했다. 필드 유효성(음수 금지 등)은 생성 시점에
강제한다.

이 인터페이스에 대해 서로 다른 우선순위 기준을 구현체로 나눠 담았다.

- `DistanceOnlyScorePolicy` — score = distanceMeters. 거리만 본다.
- `OrderWaitScorePolicy` — score = -orderWaitingMillis. 기존 FIFO를 그대로 재현하는 기준선.
- `BalancedScorePolicy` — 거리·주문 대기·드리미 대기를 각각 기준값 대비 비율로 정규화한 뒤
  `BalancedScoreWeights`로 가중합. 가중치 합이 100일 필요는 없고, 정책 내부에서 합으로 나눠 정규화한다.
- `SlaUrgencyScorePolicy` — SLA(주문 대기 한계) 초과 위험이 커질수록 거리보다 대기시간을 우선하도록,
  대기시간 초과 위험을 제곱해 페널티를 준다. 부동소수점 오차를 피하려 정수(long) 연산만 사용한다.
- `ScarcityAwareScorePolicy` — 다른 스코어 정책을 베이스로 감싸서(`baseScorePolicy`), 이 주문/드리미를
  대체할 후보가 적을수록(`orderCandidateCount`/`dreamiCandidateCount`가 작을수록) 지금 놓치면 기회를
  잃을 위험이 크므로 점수를 낮춘다. `ScarcityScoreWeights`로 베이스 점수와 희소성 보정의 비중을 조절한다.

여러 정책을 주입했을 때 실제로 결과가 달라지는지는 `AssignmentScoringPolicySubstitutionTest`에서
동일한 "경합 시나리오"(같은 드리미를 두 주문이 서로 다른 신호로 원하는 상황)에 다섯 정책을 각각 주입해
배정 결과가 정책 의도대로 갈리는지로 검증한다.
