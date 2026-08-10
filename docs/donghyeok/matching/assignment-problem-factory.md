# MatchingAssignmentProblemFactory — Eligibility → Scoring → Assignment 연결

`MatchingAssignmentProblem`의 기존 javadoc에는 "거절·만료 이력을 읽어 후보를 거르는 책임은
`MatchingAssignmentProblemFactory`에 있다"고 적혀 있었지만, 그 클래스 자체는 존재하지 않았다. 이번 변경으로
그 팩토리를 실제로 구현했다.

`MatchingAssignmentProblemFactory.create(evaluatedAt, orders, dreamis, rawCandidates)`는

1. 원시 후보로 임시 `MatchingAssignmentProblem`을 만들어 orderId/dreamiId 존재 검증·중복 검증을 재사용하고,
2. `eligibilityPolicy.isEligible(candidate, problem.evaluatedAt())`으로 후보를 필터링한 뒤,
3. 걸러진 후보로 최종 `MatchingAssignmentProblem`을 만든다.

호출부는 `LocalDateTime` 기준을 그대로 유지하며, 이렇게 만들어진 problem은 기존
`ScoreBasedGreedyAssignmentPolicy`/`LegacyOrderFirstAssignmentPolicy`에 변경 없이 그대로 흘러들어가
Scoring → Assignment를 수행한다.

**아직 안 된 것**: 이 파이프라인은 `policy/` 패키지 안에 격리된 순수 객체들의 조합일 뿐, 실제
`MatchingEngine`/`MatchingService`(레거시 `attemptOfferRound` 경로)에는 연결되지 않았다. 실제 연결에는
엔진 상태 → `MatchingOrderInput`/`MatchingDreamiInput`/`MatchingCandidate` 변환과, 나온 `MatchingPlan`을
오퍼 생성·SSE 발송으로 잇는 Dispatcher가 별도로 필요하며, 이는 런타임 동작 자체를 바꾸는 변경이라 별도
PR로 분리한다.

## MatchingPlanValidator에 적격성 검증 추가 (M2의 마지막 커밋)

`MatchingPlanValidator`는 지금까지 주문별 `maxConcurrentOffers` 초과 여부만 검증했다. 여기에 각 제안의
후보가 여전히 적격한지도 함께 검증하도록 확장했다.

- `MatchingEligibilityPolicy`를 생성자로 주입받는다(기존 무인자 생성자 제거 — 호출부 전부 갱신).
- 제안별로 `problem.candidates()`에서 `(orderId, dreamiId)`가 일치하는 후보를 찾고, 없으면
  "문제에 존재하지 않는 후보에 대한 제안입니다" 예외를 던진다(기존 orderId 존재 검증과 같은 성격).
- 찾은 후보에 대해 `eligibilityPolicy.isEligible(candidate, problem.evaluatedAt())`이 false면
  "제안할 수 없는 후보입니다" 예외를 던진다. `evaluatedAt`은 항상 `problem.evaluatedAt()`을 그대로 쓴다 —
  `MatchingAssignmentProblemFactory`와 동일하게 validator도 별도의 시각 기준을 갖지 않는다.

배정 정책(`ScoreBasedGreedyAssignmentPolicy` 등)은 이미 필터링된 `problem.candidates()`만 사용하므로
정상 흐름에서는 이 검증이 항상 통과해야 한다. 그럼에도 validator에 다시 넣은 이유는, 배정 정책 구현이
계약(`problem.candidates()`에 없는 조합을 만들지 않는다)을 어기거나 필터링을 건너뛴 problem이 잘못
전달되는 경우를 마지막 방어선에서 잡기 위함이다.
